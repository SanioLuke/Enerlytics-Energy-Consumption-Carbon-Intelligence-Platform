import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiClient } from '../../core/api/api-client';
import { ApiError } from '../../core/api/api-error';
import type { BuildingResponse, SiteResponse } from '../../core/api/contracts';
import { EchartComponent } from '../../core/charts/echart.component';
import { AuthService } from '../../core/auth/auth.service';
import { ContextService } from '../../core/context/context.service';
import { EmptyStateComponent } from '../../core/ui/empty-state.component';
import { ErrorStateComponent } from '../../core/ui/error-state.component';
import { LoadingStateComponent } from '../../core/ui/loading-state.component';
import { liveTrendOption } from './live-chart-options';
import {
  LiveEnergyService,
  type LiveScope,
} from './live-energy.service';

/**
 * Real-time energy monitoring page.
 *
 * Subscribes to a single SSE stream for the selected organization/site/building
 * and renders current demand, meter counts, selected-meter power, a live trend
 * chart, and the latest meter readings. The service throttles UI updates and
 * automatically reconnects with exponential backoff, falling back to snapshot
 * polling if SSE is unavailable.
 */
@Component({
  selector: 'app-live-energy',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    EchartComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
  ],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1 class="title">Live Energy</h1>
          <p class="subtitle">
            Real-time demand and telemetry for
            {{ scopeLabel() }}
          </p>
        </div>
        <div class="status" [class]="'status-' + service.state().status">
          <span class="status-dot"></span>
          {{ statusLabel() }}
        </div>
      </header>

      <div class="filter-bar" role="search" aria-label="Live scope filters">
        <div class="field">
          <label for="f-live-site">Site</label>
          <select
            id="f-live-site"
            [value]="scope().siteId ?? ''"
            (change)="onSite($event)"
          >
            <option value="">All sites</option>
            @for (site of sites(); track site.id) {
              <option [value]="site.id">{{ site.name }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-live-building">Building</label>
          <select
            id="f-live-building"
            [value]="scope().buildingId ?? ''"
            [disabled]="buildings().length === 0"
            (change)="onBuilding($event)"
          >
            <option value="">All buildings</option>
            @for (b of buildings(); track b.id) {
              <option [value]="b.id">{{ b.name }}</option>
            }
          </select>
        </div>

        <div class="field">
          <label for="f-live-meter">Meter</label>
          <select
            id="f-live-meter"
            [value]="scope().meterId ?? ''"
            (change)="onMeter($event)"
          >
            <option value="">All meters</option>
            @for (m of meterOptions(); track m.meterId) {
              <option [value]="m.meterId">{{ m.meterName ?? m.meterId }}</option>
            }
          </select>
        </div>

        <div class="actions">
          <button
            type="button"
            class="refresh"
            (click)="service.refresh()"
            aria-label="Reconnect live stream"
          >
            Reconnect
          </button>
        </div>
      </div>

      @if (service.state().status === 'error' && !snapshot()) {
        <app-error-state
          [error]="connectionError()"
          (retry)="service.refresh()"
        />
      }

      @if (snapshot(); as snap) {
        <div class="kpi-strip" role="list" aria-label="Live telemetry summary">
          <div class="kpi" role="listitem">
            <span class="kpi-label">Current demand</span>
            <span class="kpi-value">{{ snap.currentDemandKw | number: '1.1-1' }} kW</span>
          </div>
          <div class="kpi" role="listitem">
            <span class="kpi-label">Active meters</span>
            <span class="kpi-value">{{ snap.activeMeterCount }}</span>
          </div>
          <div class="kpi" role="listitem">
            <span class="kpi-label">Offline meters</span>
            <span class="kpi-value" [class.warn]="snap.offlineMeterCount > 0">
              {{ snap.offlineMeterCount }}
            </span>
          </div>
          @if (selectedMeter(); as m) {
            <div class="kpi" role="listitem">
              <span class="kpi-label">Meter power</span>
              <span class="kpi-value">{{ m.currentPowerKw | number: '1.1-1' }} kW</span>
              <span class="kpi-meta" [class]="'sev-' + m.status.toLowerCase()">{{ m.status }}</span>
            </div>
          }
        </div>

        <div class="grid">
          <div class="card span-8">
            <div class="card-header">
              <h2 class="card-title">Live demand trend</h2>
              @if (service.state().lastUpdated) {
                <span class="updated">
                  Last update: {{ service.state().lastUpdated | date: 'HH:mm:ss' : 'UTC' }} UTC
                </span>
              }
            </div>
            @if (snap.recentTrend.length === 0) {
              <app-empty-state heading="No trend data yet" icon="show_chart">
                Trend points appear as snapshots arrive.
              </app-empty-state>
            } @else {
              <div class="chart-host">
                <app-echart
                  [option]="trendChart()!"
                  ariaLabel="Live demand trend in kilowatts"
                />
              </div>
            }
          </div>

          <div class="card span-4 meter-panel">
            <div class="card-header">
              <h2 class="card-title">Meters</h2>
              <span class="updated">
                {{ snap.activeMeterCount }} active / {{ snap.offlineMeterCount }} offline
              </span>
            </div>
            @if (snap.meterReadings.length === 0) {
              <app-empty-state heading="No meter readings" icon="speed">
                No meters reported for this scope.
              </app-empty-state>
            } @else {
              <ul class="meter-list" aria-label="Meter readings">
                @for (m of snap.meterReadings; track m.meterId) {
                  <li class="meter-row" [class]="'meter-' + m.status.toLowerCase()">
                    <div class="meter-info">
                      <span class="meter-name">{{ m.meterName ?? m.meterId }}</span>
                      <span class="meter-meta">
                        {{ m.currentPowerKw | number: '1.1-1' }} kW ·
                        {{ m.lastSeenAt | date: 'HH:mm:ss' : 'UTC' }} UTC
                      </span>
                    </div>
                    <span class="badge" [class]="'badge-' + m.status.toLowerCase()">
                      {{ m.status }}
                    </span>
                  </li>
                }
              </ul>
            }
          </div>
        </div>
      } @else {
        <app-loading-state variant="page" label="Connecting to live telemetry" />
      }
    </div>
  `,
  styles: `
    .page { display: flex; flex-direction: column; gap: var(--ely-space-5); }
    .page-header {
      display: flex; align-items: flex-end; justify-content: space-between;
      gap: var(--ely-space-4);
    }
    .title { margin: 0; font: var(--ely-text-title); }
    .subtitle { margin: var(--ely-space-1) 0 0; font: var(--ely-text-small); color: var(--ely-text-2); }

    .status {
      display: inline-flex; align-items: center; gap: var(--ely-space-2);
      padding: var(--ely-space-1) var(--ely-space-3);
      border-radius: var(--ely-radius-full);
      font: var(--ely-text-caption); font-weight: 500; text-transform: uppercase;
      background: var(--ely-surface-raised); border: 1px solid var(--ely-border);
    }
    .status-dot { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
    .status-connected { color: var(--ely-success); }
    .status-connecting, .status-reconnecting { color: var(--ely-warning); }
    .status-fallback { color: var(--ely-info); }
    .status-error, .status-idle { color: var(--ely-critical); }

    .filter-bar {
      display: flex; align-items: flex-end; flex-wrap: wrap; gap: var(--ely-space-3);
      padding: var(--ely-space-3) var(--ely-space-4);
      background: var(--ely-surface); border: 1px solid var(--ely-border);
      border-radius: var(--ely-radius-md);
    }
    .field { display: flex; flex-direction: column; gap: var(--ely-space-1); }
    .field label { font: var(--ely-text-caption); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ely-text-3); }
    .field select {
      height: 32px; padding: 0 var(--ely-space-2); min-width: 160px;
      border: 1px solid var(--ely-border-strong); border-radius: var(--ely-radius-sm);
      background: var(--ely-surface); color: var(--ely-text); font: var(--ely-text-body);
    }
    .field select:disabled { opacity: 0.5; }
    .actions { margin-left: auto; }
    .refresh {
      height: 32px; padding: 0 var(--ely-space-3);
      border: 1px solid var(--ely-border-strong); border-radius: var(--ely-radius-sm);
      background: var(--ely-surface); color: var(--ely-text); font: var(--ely-text-body);
      cursor: pointer;
    }
    .refresh:hover { border-color: var(--ely-accent); color: var(--ely-accent); }

    .kpi-strip {
      display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--ely-space-3);
    }
    .kpi {
      display: flex; flex-direction: column; gap: var(--ely-space-1);
      padding: var(--ely-space-3) var(--ely-space-4);
      background: var(--ely-surface); border: 1px solid var(--ely-border); border-radius: var(--ely-radius-md);
    }
    .kpi-label { font: var(--ely-text-caption); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ely-text-3); }
    .kpi-value { font: var(--ely-text-kpi); color: var(--ely-text); }
    .kpi-meta { font: var(--ely-text-small); color: var(--ely-text-2); }
    .warn { color: var(--ely-warning); }
    .sev-online { color: var(--ely-success); }
    .sev-offline { color: var(--ely-critical); }

    .grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: var(--ely-space-5); }
    .span-8 { grid-column: span 8; }
    .span-4 { grid-column: span 4; }

    .card {
      display: flex; flex-direction: column; gap: var(--ely-space-3);
      padding: var(--ely-space-4);
      background: var(--ely-surface); border: 1px solid var(--ely-border); border-radius: var(--ely-radius-md);
      min-height: 360px;
    }
    .card-header { display: flex; align-items: baseline; justify-content: space-between; }
    .card-title { margin: 0; font: var(--ely-text-section); }
    .updated { font: var(--ely-text-micro); color: var(--ely-text-3); }
    .chart-host { flex: 1 1 auto; min-height: 280px; }

    .meter-panel { max-height: 560px; }
    .meter-list {
      list-style: none; margin: 0; padding: 0;
      display: flex; flex-direction: column; overflow-y: auto;
    }
    .meter-row {
      display: flex; align-items: center; justify-content: space-between; gap: var(--ely-space-2);
      padding: var(--ely-space-2) var(--ely-space-2);
      border-bottom: 1px solid var(--ely-border);
    }
    .meter-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .meter-name { font: var(--ely-text-body); font-weight: 500; color: var(--ely-text); }
    .meter-meta { font: var(--ely-text-micro); color: var(--ely-text-3); }
    .badge {
      padding: 2px var(--ely-space-2);
      border-radius: var(--ely-radius-sm);
      font: var(--ely-text-caption); font-weight: 500;
      background: var(--ely-surface-raised); color: var(--ely-text-2);
    }
    .badge-online { background: var(--ely-success-subtle); color: var(--ely-success); }
    .badge-offline { background: var(--ely-critical-subtle); color: var(--ely-critical); }

    @media (max-width: 1023px) {
      .kpi-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .span-8, .span-4 { grid-column: span 12; }
      .filter-bar { flex-direction: column; align-items: stretch; }
      .field select { width: 100%; }
      .actions { margin-left: 0; }
    }
  `,
})
export class LiveEnergyComponent {
  protected readonly service = inject(LiveEnergyService);
  private readonly api = inject(ApiClient);
  private readonly auth = inject(AuthService);
  private readonly context = inject(ContextService);
  private readonly destroyRef = inject(DestroyRef);

  /** Local scope — meter selection is not persisted globally. */
  protected readonly scope = signal<LiveScope>({
    siteId: this.context.scope().siteId,
    buildingId: this.context.scope().buildingId,
    meterId: null,
  });

  protected readonly sites = signal<SiteResponse[]>([]);
  protected readonly buildings = signal<BuildingResponse[]>([]);

  constructor() {
    // Load sites once for the active organization.
    const orgId = this.auth.activeOrganization()?.organizationId;
    if (orgId) {
      this.api
        .get<{ content: SiteResponse[] }>(this.api.orgPath(orgId, '/sites'), {
          size: 100,
          sort: 'name,asc',
        })
        .pipe(
          catchError(() => of({ content: [] })),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((page) => this.sites.set(page.content));
    }

    // React to site selection changes to load buildings and reconnect.
    effect(() => {
      const siteId = this.scope().siteId;
      if (siteId && orgId) {
        this.api
          .get<{ content: BuildingResponse[] }>(
            this.api.orgPath(orgId, `/sites/${siteId}/buildings`),
            { size: 100, sort: 'name,asc' },
          )
          .pipe(
            catchError(() => of({ content: [] })),
            takeUntilDestroyed(this.destroyRef),
          )
          .subscribe((page) => this.buildings.set(page.content));
      } else {
        this.buildings.set([]);
      }
    });

    // Connect whenever scope changes.
    effect(() => {
      this.service.connect(this.scope());
    });
  }

  protected readonly snapshot = computed(() => this.service.filteredSnapshot());
  protected readonly meterOptions = computed(
    () => this.service.snapshot()?.meterReadings ?? [],
  );
  protected readonly selectedMeter = computed(() => {
    const id = this.scope().meterId;
    if (!id) return null;
    return this.service.snapshot()?.meterReadings.find((m) => m.meterId === id) ?? null;
  });

  protected readonly scopeLabel = computed(() => {
    const s = this.scope();
    const site = this.sites().find((x) => x.id === s.siteId)?.name;
    const building = this.buildings().find((x) => x.id === s.buildingId)?.name;
    return [site, building, s.meterId ? 'single meter' : null]
      .filter(Boolean)
      .join(' · ') || 'all sites';
  });

  protected readonly connectionError = computed(
    () =>
      new ApiError({
        status: -1,
        code: 'SSE_UNAVAILABLE',
        detail: this.service.state().error ?? 'Live stream unavailable',
      }),
  );

  protected readonly statusLabel = computed(() => {
    const status = this.service.state().status;
    switch (status) {
      case 'connected':
        return 'Live';
      case 'connecting':
        return 'Connecting';
      case 'reconnecting':
        return `Reconnecting (${this.service.state().attempt})`;
      case 'fallback':
        return 'Snapshot fallback';
      case 'error':
        return 'Connection error';
      default:
        return 'Idle';
    }
  });

  protected readonly trendChart = computed(() => {
    const snap = this.snapshot();
    if (!snap || snap.recentTrend.length === 0) return null;
    return liveTrendOption(snap.recentTrend, 'UTC');
  });

  protected onSite(event: Event): void {
    const siteId = (event.target as HTMLSelectElement).value || null;
    this.scope.update((s) => ({
      ...s,
      siteId,
      buildingId: null,
      meterId: null,
    }));
    this.context.setScope(siteId);
  }

  protected onBuilding(event: Event): void {
    const buildingId = (event.target as HTMLSelectElement).value || null;
    this.scope.update((s) => ({ ...s, buildingId, meterId: null }));
  }

  protected onMeter(event: Event): void {
    const meterId = (event.target as HTMLSelectElement).value || null;
    this.scope.update((s) => ({ ...s, meterId }));
  }
}
