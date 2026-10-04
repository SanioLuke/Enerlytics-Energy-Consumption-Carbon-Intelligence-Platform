import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../core/auth/auth.service';
import type { LiveEnergySnapshot } from '../../core/api/contracts';
import {
  LIVE_ENERGY_CONFIG,
  LiveEnergyService,
  type LiveEnergyConfig,
} from './live-energy.service';

type MessageHandler = (event: MessageEvent) => void;
type OpenHandler = () => void;
type ErrorHandler = () => void;

class MockEventSource {
  readonly url: string;
  readonly withCredentials = false;
  readonly CONNECTING = 0 as const;
  readonly OPEN = 1 as const;
  readonly CLOSED = 2 as const;
  readonly readyState = 1 as const;

  onopen: OpenHandler | null = null;
  onmessage: MessageHandler | null = null;
  onerror: ErrorHandler | null = null;

  static last: MockEventSource | null = null;

  constructor(url: string) {
    this.url = url;
    MockEventSource.last = this;
  }

  close(): void {
    MockEventSource.last = null;
  }

  dispatch(type: 'open' | 'message' | 'error', data?: unknown): void {
    if (type === 'open') this.onopen?.();
    if (type === 'error') this.onerror?.();
    if (type === 'message') {
      this.onmessage?.(
        new MessageEvent('message', { data: JSON.stringify(data) }),
      );
    }
  }
}

const CONFIG: LiveEnergyConfig = {
  maxAttempts: 1,
  baseDelayMs: 10,
  maxDelayMs: 100,
  throttleMs: 0,
  fallbackIntervalMs: 20,
};

class StubAuthService {
  readonly activeOrganization = signal<{ organizationId: string; organizationName: string; roles: string[]; permissions: string[] } | null>({
    organizationId: 'org-1',
    organizationName: 'Demo',
    roles: [],
    permissions: ['telemetry:read'],
  });
}

describe('LiveEnergyService', () => {
  let service: LiveEnergyService;
  let http: HttpTestingController;

  beforeEach(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (globalThis as any).EventSource = MockEventSource as unknown as typeof EventSource;
    MockEventSource.last = null;

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        LiveEnergyService,
        { provide: LIVE_ENERGY_CONFIG, useValue: CONFIG },
        { provide: AuthService, useClass: StubAuthService },
      ],
    });

    service = TestBed.inject(LiveEnergyService);
    http = TestBed.inject(HttpTestingController);
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    service.disconnect();
    http.verify();
    vi.useRealTimers();
  });

  const snapshot = (overrides: Partial<LiveEnergySnapshot> = {}): LiveEnergySnapshot => ({
    generatedAt: '2026-10-04T12:00:00Z',
    organizationId: 'org-1',
    currentDemandKw: 100,
    activeMeterCount: 2,
    offlineMeterCount: 0,
    meterReadings: [],
    recentTrend: [],
    ...overrides,
  });

  it('reports an error when no organization is active', () => {
    const auth = TestBed.inject(AuthService) as unknown as StubAuthService;
    auth.activeOrganization.set(null);
    service.connect({ siteId: null, buildingId: null, meterId: null });
    expect(service.state().status).toBe('error');
    expect(service.state().error).toContain('No active organization');
  });

  it('connects via EventSource and marks status connected on open', () => {
    service.connect({ siteId: null, buildingId: null, meterId: null });
    MockEventSource.last?.dispatch('open');

    expect(service.state().status).toBe('connected');
    expect(MockEventSource.last?.url).toContain(
      'http://localhost:8080/api/v1/organizations/org-1/live/energy/subscribe',
    );
  });

  it('parses incoming snapshots and exposes them via a throttled signal', () => {
    service.connect({ siteId: null, buildingId: null, meterId: null });
    MockEventSource.last?.dispatch('open');

    const snap = snapshot({ currentDemandKw: 123 });
    MockEventSource.last?.dispatch('message', snap);

    expect(service.snapshot()?.currentDemandKw).toBe(123);
    expect(service.state().lastUpdated).toBe(snap.generatedAt);
  });

  it('reconnects with exponential backoff after an error', () => {
    service.connect({ siteId: null, buildingId: null, meterId: null });
    MockEventSource.last?.dispatch('open');
    const first = MockEventSource.last;

    MockEventSource.last?.dispatch('error');
    expect(service.state().status).toBe('reconnecting');
    expect(service.state().attempt).toBe(1);

    vi.advanceTimersByTime(15);
    expect(MockEventSource.last).not.toBe(first);
    MockEventSource.last?.dispatch('open');
    expect(service.state().status).toBe('connected');
    expect(service.state().attempt).toBe(0);
  });

  it('falls back to snapshot polling after max retry attempts', () => {
    service.connect({ siteId: null, buildingId: null, meterId: null });

    // With maxAttempts=1, the first error reconnects, the second falls back.
    MockEventSource.last?.dispatch('error');
    vi.advanceTimersByTime(20);
    MockEventSource.last?.dispatch('error');
    vi.advanceTimersByTime(20);

    expect(service.state().status).toBe('fallback');

    const polls = http
      .match((req) => req.url.includes('/api/v1/organizations/org-1/live/energy/snapshot'))
      .filter((r) => !r.cancelled);
    expect(polls.length).toBeGreaterThanOrEqual(1);
    polls[0].flush(snapshot({ currentDemandKw: 55 }));

    expect(service.snapshot()?.currentDemandKw).toBe(55);
  });

  it('includes site and building filters in the stream URL', () => {
    service.connect({ siteId: 'site-1', buildingId: 'b-1', meterId: null });
    expect(MockEventSource.last?.url).toContain('siteId=site-1');
    expect(MockEventSource.last?.url).toContain('buildingId=b-1');
  });

  it('filters meter readings when a meter scope is selected', () => {
    service.connect({ siteId: null, buildingId: null, meterId: null });
    MockEventSource.last?.dispatch('open');

    const snap = snapshot({
      meterReadings: [
        { meterId: 'm-1', meterName: 'A', currentPowerKw: 10, lastSeenAt: '', status: 'ONLINE' },
        { meterId: 'm-2', meterName: 'B', currentPowerKw: 20, lastSeenAt: '', status: 'ONLINE' },
      ],
    });
    MockEventSource.last?.dispatch('message', snap);

    service.connect({ siteId: null, buildingId: null, meterId: 'm-2' });
    expect(service.filteredSnapshot()?.meterReadings).toHaveLength(1);
    expect(service.filteredSnapshot()?.meterReadings[0].meterId).toBe('m-2');
  });
});
