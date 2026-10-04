import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, type Subscription, interval } from 'rxjs';
import { catchError, sampleTime, startWith, switchMap } from 'rxjs/operators';
import type { LiveEnergySnapshot } from '../../core/api/contracts';
import { ApiClient } from '../../core/api/api-client';
import { AuthService } from '../../core/auth/auth.service';
import { TokenStorage } from '../../core/auth/token-storage';
import { environment } from '../../../environments/environment';

export interface LiveEnergyConfig {
  maxAttempts: number;
  baseDelayMs: number;
  maxDelayMs: number;
  throttleMs: number;
  fallbackIntervalMs: number;
}

export const LIVE_ENERGY_CONFIG = new InjectionToken<LiveEnergyConfig>('LIVE_ENERGY_CONFIG');

export const DEFAULT_LIVE_ENERGY_CONFIG: LiveEnergyConfig = {
  maxAttempts: 5,
  baseDelayMs: 1000,
  maxDelayMs: 30_000,
  throttleMs: 500,
  fallbackIntervalMs: 5000,
};

export type LiveConnectionStatus =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'error'
  | 'fallback';

export interface LiveScope {
  siteId: string | null;
  buildingId: string | null;
  meterId: string | null;
}

export interface LiveEnergyState {
  status: LiveConnectionStatus;
  snapshot: LiveEnergySnapshot | null;
  lastUpdated: string | null;
  error: string | null;
  attempt: number;
}

/**
 * Manages the Server-Sent Events connection for live energy telemetry.
 *
 * Reads are throttled to at most one UI update per throttle window even if the
 * backend sends many events. Reconnection uses exponential backoff with a
 * bounded maximum; after exhausting retries the service falls back to periodic
 * polling of the snapshot endpoint. There is no long-lived subscription leak:
 * `disconnect()` closes the EventSource, timers, and the fallback interval.
 */
@Injectable({ providedIn: 'root' })
export class LiveEnergyService {
  private readonly api = inject(ApiClient);
  private readonly auth = inject(AuthService);
  private readonly tokenStorage = inject(TokenStorage);

  private eventSource: EventSource | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private fallbackSub: Subscription | null = null;
  private attempt = 0;

  private readonly raw$ = new Subject<LiveEnergySnapshot>();
  private readonly currentScope = signal<LiveScope | null>(null);
  private readonly config: LiveEnergyConfig =
    inject(LIVE_ENERGY_CONFIG, { optional: true }) ?? DEFAULT_LIVE_ENERGY_CONFIG;

  /** Throttled snapshot stream — protects the UI from high-frequency updates. */
  readonly snapshot = toSignal(
    this.config.throttleMs > 0
      ? this.raw$.pipe(sampleTime(this.config.throttleMs))
      : this.raw$,
    { initialValue: null },
  );

  readonly state = signal<LiveEnergyState>({
    status: 'idle',
    snapshot: null,
    lastUpdated: null,
    error: null,
    attempt: 0,
  });

  readonly filteredSnapshot = computed(() => {
    const snap = this.snapshot();
    const scope = this.currentScope();
    if (!snap || !scope?.meterId) {
      return snap;
    }
    return {
      ...snap,
      meterReadings: snap.meterReadings.filter(
        (m) => m.meterId === scope.meterId,
      ),
    };
  });

  connect(scope: LiveScope): void {
    const orgId = this.auth.activeOrganization()?.organizationId;
    if (!orgId) {
      this.state.set({
        status: 'error',
        snapshot: null,
        lastUpdated: null,
        error: 'No active organization',
        attempt: 0,
      });
      return;
    }
    this.disconnect(false);
    this.currentScope.set(scope);
    this.attempt = 0;
    this.open(orgId, scope);
  }

  disconnect(reset = true): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.fallbackSub?.unsubscribe();
    this.fallbackSub = null;
    if (reset) {
      this.state.set({
        status: 'idle',
        snapshot: null,
        lastUpdated: null,
        error: null,
        attempt: 0,
      });
      this.currentScope.set(null);
    }
  }

  refresh(): void {
    const scope = this.currentScope();
    if (scope) {
      this.connect(scope);
    }
  }

  private open(orgId: string, scope: LiveScope): void {
    const url = this.streamUrl(orgId, scope);
    const EventSourceCtor = (globalThis as typeof globalThis & { EventSource?: typeof EventSource }).EventSource;
    if (!EventSourceCtor) {
      this.startFallback(orgId, scope, 'EventSource not available');
      return;
    }

    const es = new EventSourceCtor(url);
    this.eventSource = es;

    this.state.update(() => ({
      snapshot: null,
      lastUpdated: null,
      status: 'connecting',
      error: null,
      attempt: this.attempt,
    }));

    es.onopen = () => {
      this.attempt = 0;
      this.state.update((s) => ({
        ...s,
        status: 'connected',
        error: null,
        attempt: 0,
      }));
    };

    es.onmessage = (event: MessageEvent) => {
      try {
        const snapshot: LiveEnergySnapshot = JSON.parse(event.data);
        this.raw$.next(snapshot);
        this.state.update((s) => ({
          ...s,
          lastUpdated: snapshot.generatedAt,
          error: null,
        }));
      } catch {
        this.state.update((s) => ({
          ...s,
          error: 'Malformed live snapshot',
        }));
      }
    };

    es.onerror = () => {
      this.handleError(orgId, scope);
    };
  }

  private handleError(orgId: string, scope: LiveScope): void {
    this.disconnect(false);
    this.attempt++;
    if (this.attempt > this.config.maxAttempts) {
      this.startFallback(
        orgId,
        scope,
        'Live stream unavailable; using snapshot fallback',
      );
      return;
    }
    const delay = Math.min(
      this.config.baseDelayMs * 2 ** (this.attempt - 1),
      this.config.maxDelayMs,
    );
    this.state.update((s) => ({
      ...s,
      status: 'reconnecting',
      error: `Reconnecting in ${delay}ms (attempt ${this.attempt})`,
      attempt: this.attempt,
    }));
    this.reconnectTimer = setTimeout(() => this.open(orgId, scope), delay);
  }

  private startFallback(orgId: string, scope: LiveScope, message: string): void {
    this.state.set({
      status: 'fallback',
      snapshot: null,
      lastUpdated: null,
      error: message,
      attempt: this.attempt,
    });
    const params: Record<string, string> = {};
    if (scope.siteId) params['siteId'] = scope.siteId;
    if (scope.buildingId) params['buildingId'] = scope.buildingId;

    this.fallbackSub = interval(this.config.fallbackIntervalMs)
      .pipe(
        startWith(0),
        switchMap(() =>
          this.api.get<LiveEnergySnapshot>(
            this.api.orgPath(orgId, '/live/energy/snapshot'),
            params,
          ),
        ),
        catchError(() => EMPTY),
      )
      .subscribe((snapshot) => {
        if (snapshot) {
          this.raw$.next(snapshot);
          this.state.update((s) => ({
            ...s,
            lastUpdated: snapshot.generatedAt,
          }));
        }
      });
  }

  private streamUrl(orgId: string, scope: LiveScope): string {
    const params = new URLSearchParams();
    if (scope.siteId) params.set('siteId', scope.siteId);
    if (scope.buildingId) params.set('buildingId', scope.buildingId);
    const token = this.tokenStorage.accessToken();
    if (token) params.set('access_token', token);
    const query = params.toString();
    const path = this.api.orgPath(orgId, '/live/energy/subscribe');
    return `${environment.apiBaseUrl}${path}${query ? '?' + query : ''}`;
  }
}
