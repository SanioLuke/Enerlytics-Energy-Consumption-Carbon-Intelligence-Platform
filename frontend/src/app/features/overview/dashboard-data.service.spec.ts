import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type {
  EnergyAggregateResponse,
  PageResponse,
  SiteResponse,
} from '../../core/api/contracts';
import { apiErrorInterceptor } from '../../core/api/api-error.interceptor';
import { ContextService } from '../../core/context/context.service';
import {
  DashboardDataService,
  resolveRange,
  sum,
} from './dashboard-data.service';

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

class StubContextService {
  readonly organizationId = signal<string | null>('org-1');
  readonly scope = signal({ siteId: null as string | null, buildingId: null });
  readonly period = signal('TODAY');
  readonly customFrom = signal<string | null>(null);
  readonly customTo = signal<string | null>(null);
  readonly comparison = signal<'NONE' | 'PRIOR_PERIOD'>('NONE');
}

const SITES: PageResponse<SiteResponse> = {
  content: [
    {
      id: 'site-1',
      organizationId: 'org-1',
      code: 'HQ',
      name: 'Headquarters',
      active: true,
      timezone: 'America/New_York',
      currency: 'USD',
      createdAt: '',
      updatedAt: '',
    },
    {
      id: 'site-2',
      organizationId: 'org-1',
      code: 'PLT',
      name: 'Plant',
      active: true,
      timezone: 'UTC',
      currency: 'EUR',
      createdAt: '',
      updatedAt: '',
    },
  ],
  page: 0,
  size: 100,
  totalElements: 2,
  totalPages: 1,
};

function energyBucket(
  kwh: number,
  overrides: Partial<EnergyAggregateResponse> = {},
): EnergyAggregateResponse {
  return {
    dimension: 'ORGANIZATION',
    dimensionId: 'org-1',
    granularity: 'HOUR',
    bucketStart: '2026-10-04T10:00:00Z',
    bucketEnd: '2026-10-04T11:00:00Z',
    energyConsumedKwh: kwh,
    readingCount: 12,
    estimatedReadingCount: 0,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Pure helpers
// ---------------------------------------------------------------------------

describe('resolveRange', () => {
  const now = new Date('2026-10-04T14:30:00Z');

  it('maps TODAY to start-of-day UTC → now with HOUR granularity', () => {
    const range = resolveRange('TODAY', null, null, now);
    expect(range.from).toBe('2026-10-04T00:00:00.000Z');
    expect(range.to).toBe(now.toISOString());
    expect(range.granularity).toBe('HOUR');
  });

  it('maps YESTERDAY to the full prior UTC day', () => {
    const range = resolveRange('YESTERDAY', null, null, now);
    expect(range.from).toBe('2026-10-03T00:00:00.000Z');
    expect(range.to).toBe('2026-10-04T00:00:00.000Z');
    expect(range.granularity).toBe('HOUR');
  });

  it('maps LAST_7_DAYS with DAY granularity', () => {
    const range = resolveRange('LAST_7_DAYS', null, null, now);
    expect(range.granularity).toBe('DAY');
    expect(Date.parse(range.to) - Date.parse(range.from)).toBe(7 * 86_400_000);
  });

  it('honors a valid CUSTOM range and picks MONTH for >120-day spans', () => {
    const range = resolveRange('CUSTOM', '2026-01-01', '2026-09-30', now);
    expect(range.from).toBe('2026-01-01T00:00:00.000Z');
    expect(range.to).toBe('2026-10-01T00:00:00.000Z'); // inclusive end → next day
    expect(range.granularity).toBe('MONTH');
  });

  it('falls back to last 24h when CUSTOM bounds are missing', () => {
    const range = resolveRange('CUSTOM', null, null, now);
    expect(Date.parse(range.to) - Date.parse(range.from)).toBe(86_400_000);
  });
});

describe('sum', () => {
  it('treats null/undefined members as zero', () => {
    expect(sum([{ v: 1 }, { v: null }, { v: 3 }], (i) => i.v)).toBe(4);
  });
});

// ---------------------------------------------------------------------------
// Service — request URLs carry the discriminating query params, so each
// branch is matched precisely (energy range vs today share only dimension).
// ---------------------------------------------------------------------------

describe('DashboardDataService', () => {
  let service: DashboardDataService;
  let http: HttpTestingController;
  let context: StubContextService;

  const has = (s: string) => (r: { urlWithParams: string }) =>
    r.urlWithParams.includes(s);
  const energyOrg = (r: { urlWithParams: string }) =>
    r.urlWithParams.includes('/analytics/energy') &&
    r.urlWithParams.includes('dimension=ORGANIZATION');
  const energySite = (r: { urlWithParams: string }) =>
    r.urlWithParams.includes('/analytics/energy') &&
    r.urlWithParams.includes('dimension=SITE');
  const carbon = (granularity: string) => (r: { urlWithParams: string }) =>
    r.urlWithParams.includes('/analytics/carbon/trend') &&
    r.urlWithParams.includes(`granularity=${granularity}`);
  const cost = (granularity: string) => (r: { urlWithParams: string }) =>
    r.urlWithParams.includes('/billing/costs') &&
    r.urlWithParams.includes(`granularity=${granularity}`);

  type Pred = Parameters<HttpTestingController['match']>[0];

  /** Matches only live requests — superseded batches cancelled by switchMap
   *  still appear in http.match() and must be ignored. */
  const open = (pred: Pred) =>
    http.match(pred).filter((r) => !r.cancelled);
  const one = (pred: Pred) => {
    const matches = open(pred);
    if (matches.length !== 1) {
      throw new Error(
        `Expected one live request, found ${matches.length}`,
      );
    }
    return matches[0];
  };

  beforeEach(() => {
    context = new StubContextService();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
        DashboardDataService,
        { provide: ContextService, useValue: context },
      ],
    });
    service = TestBed.inject(DashboardDataService);
    http = TestBed.inject(HttpTestingController);
    // toObservable(query) emits through a signal effect — flush it so the
    // request batch exists before assertions/matching run.
    TestBed.tick();
  });

  afterEach(() => http.verify());

  interface FlushOverrides {
    rangeEnergy?: EnergyAggregateResponse[];
    todayEnergy?: EnergyAggregateResponse[];
    siteSlices?: EnergyAggregateResponse[][];
  }

  /**
   * Flushes every branch of the default (TODAY, no-compare) request batch.
   * TODAY's range request and the today window share the same URL, so
   * match() is used and the two identical requests get the same body —
   * distinguishing values are put in the site slices instead.
   */
  function flushDefault(overrides: FlushOverrides = {}): void {
    one(has('/sites')).flush(SITES);

    // Org-dimension energy calls: [range, today] in issue order.
    const energyCalls = open(energyOrg);
    energyCalls.forEach((req, i) =>
      req.flush(
        i === 0
          ? (overrides.rangeEnergy ?? [energyBucket(10), energyBucket(20)])
          : (overrides.todayEnergy ?? [
              energyBucket(40, {
                averagePowerKw: 55,
                peakPowerKw: 78,
                dataCompletenessPercentage: 100,
              }),
            ]),
      ),
    );

    const siteCalls = open(energySite);
    const sliceBodies = overrides.siteSlices ?? [
      [energyBucket(100)],
      [energyBucket(60)],
    ];
    siteCalls.forEach((req, i) => req.flush(sliceBodies[i] ?? []));

    one(carbon('HOUR')).flush([
      {
        bucketStart: '2026-10-04T00:00:00Z',
        emissionsKgCo2Eq: 500,
        estimated: false,
        qualityStatus: 'VALID',
      },
    ]);
    one(carbon('DAY')).flush([
      {
        bucketStart: '2026-10-04T00:00:00Z',
        emissionsKgCo2Eq: 420,
        estimated: false,
        qualityStatus: 'VALID',
      },
    ]);
    one(cost('HOUR')).flush([
      {
        bucketStart: '2026-10-04T00:00:00Z',
        totalCost: 31.5,
        currency: 'USD',
        qualityStatus: 'VALID',
        missingRateHours: 0,
      },
    ]);
    one(cost('DAY')).flush([
      {
        bucketStart: '2026-10-04T00:00:00Z',
        totalCost: 31.5,
        currency: 'USD',
        qualityStatus: 'VALID',
        missingRateHours: 0,
      },
    ]);
    one(has('/intensity/current')).flush({
      zone: 'US-PJM',
      carbonIntensityGCo2EqPerKwh: 412,
      estimated: false,
      retrievedAt: '2026-10-04T14:00:00Z',
    });
    one(has('status=OPEN')).flush([
      {
        id: 'a1',
        severity: 'CRITICAL',
        status: 'OPEN',
        alertType: 'HIGH_DEMAND',
        triggeredAt: '2026-10-04T13:00:00Z',
      },
      {
        id: 'a2',
        severity: 'WARNING',
        status: 'OPEN',
        alertType: 'METER_OFFLINE',
        triggeredAt: '2026-10-04T12:00:00Z',
      },
    ]);
    one(has('status=ACKNOWLEDGED')).flush([]);
  }

  it('loads a cohesive dashboard and derives real KPI values', () => {
    flushDefault();
    const state = service.state();

    expect(state.status).toBe('ready');
    if (state.status !== 'ready') {
      return;
    }
    const d = state.data;

    expect(d.kpis.currentDemand.value).toBe(55);
    expect(d.kpis.currentDemand.unit).toBe('kW');
    expect(d.kpis.todayConsumption.value).toBe(40);
    expect(d.kpis.todayConsumption.unit).toBe('kWh');
    expect(d.kpis.todayCost.value).toBe(31.5);
    expect(d.kpis.todayCost.unit).toBe('USD');
    expect(d.kpis.todayCarbon.value).toBeCloseTo(0.42);
    expect(d.kpis.todayCarbon.unit).toBe('tCO₂e');
    expect(d.kpis.gridIntensity.value).toBe(412);
    expect(d.kpis.gridIntensity.unit).toBe('gCO₂e/kWh');
    expect(d.kpis.gridIntensity.zone).toBe('US-PJM');
    expect(d.kpis.activeAlerts.value).toBe(2);
    expect(d.kpis.renewablePct.value).toBeNull();
    expect(d.kpis.renewablePct.quality).toBe('UNAVAILABLE');

    expect(d.consumption).toHaveLength(2);
    expect(d.siteEnergy.map((s) => s.energyKwh)).toEqual([100, 60]);
    expect(d.currency).toBe('USD');
    expect(d.displayTimezone).toBe('America/New_York');
    expect(d.alerts.open).toHaveLength(2);
  });

  it('marks KPIs UNAVAILABLE when buckets are empty — never zero', () => {
    one(has('/sites')).flush(SITES);
    open(energyOrg).forEach((r) => r.flush([]));
    open(energySite).forEach((r) => r.flush([]));
    open(carbon('HOUR')).forEach((r) => r.flush([]));
    open(carbon('DAY')).forEach((r) => r.flush([]));
    open(cost('HOUR')).forEach((r) => r.flush([]));
    open(cost('DAY')).forEach((r) => r.flush([]));
    one(has('/intensity/current')).flush({});
    one(has('status=OPEN')).flush([]);
    one(has('status=ACKNOWLEDGED')).flush([]);

    const state = service.state();
    expect(state.status).toBe('ready');
    if (state.status !== 'ready') {
      return;
    }
    expect(state.data.kpis.todayConsumption.value).toBeNull();
    expect(state.data.kpis.todayConsumption.quality).toBe('UNAVAILABLE');
    expect(state.data.kpis.currentDemand.value).toBeNull();
    expect(state.data.kpis.todayCarbon.value).toBeNull();
    expect(state.data.kpis.activeAlerts.value).toBe(0);
  });

  it('scopes the query dimension to SITE when a site is selected', () => {
    context.scope.set({ siteId: 'site-1', buildingId: null });
    TestBed.tick();

    one(has('/sites')).flush(SITES);
    open(energyOrg).forEach((r) => r.flush([]));
    // All energy calls are SITE-scoped now (range, today) — no per-site fan-out.
    open(energySite).forEach((r) => r.flush([]));
    open((r) => r.urlWithParams.includes('/analytics/carbon/trend'))
      .forEach((r) => r.flush([]));
    open((r) => r.urlWithParams.includes('/billing/costs'))
      .forEach((r) => r.flush([]));
    one(has('/intensity/current')).flush({});
    one(has('status=OPEN')).flush([]);
    one(has('status=ACKNOWLEDGED')).flush([]);
    // Site-comparison fan-out does not run at site scope.
    expect(
      open((r) => r.urlWithParams.includes('dimensionId=site-2')),
    ).toHaveLength(0);

    const state = service.state();
    expect(state.status).toBe('ready');
    if (state.status !== 'ready') {
      return;
    }
    expect(state.query.dimension).toBe('SITE');
    expect(state.query.dimensionId).toBe('site-1');
  });

  it('isolates a section failure without sinking the page', () => {
    one(has('/sites')).flush(SITES);
    open(energyOrg).forEach((r) => r.flush([energyBucket(5)]));
    open(energySite).forEach((r) => r.flush([]));
    open((r) => r.urlWithParams.includes('/analytics/carbon/trend'))
      .forEach((r) =>
        r.flush(
          { code: 'ACCESS_DENIED', detail: 'Missing carbon:read' },
          { status: 403, statusText: 'Forbidden' },
        ),
      );
    open((r) => r.urlWithParams.includes('/billing/costs'))
      .forEach((r) => r.flush([]));
    one(has('/intensity/current')).flush({});
    one(has('status=OPEN')).flush([]);
    one(has('status=ACKNOWLEDGED')).flush([]);

    const state = service.state();
    expect(state.status).toBe('ready');
    if (state.status !== 'ready') {
      return;
    }
    expect(state.data.sectionErrors['carbon']?.status).toBe(403);
    // Energy KPIs still derive — the page did not sink.
    expect(state.data.kpis.todayConsumption.value).toBe(5);
  });

  it('fetches previous-period series and computes KPI deltas', () => {
    context.comparison.set('PRIOR_PERIOD');
    TestBed.tick();

    one(has('/sites')).flush(SITES);
    // [range, previous-range, today, yesterday-window] — all ORGANIZATION/HOUR.
    const energyCalls = open(energyOrg);
    const bodies = [
      [energyBucket(10)],
      [energyBucket(8)],
      [energyBucket(40)],
      [energyBucket(35)],
    ];
    energyCalls.forEach((r, i) => r.flush(bodies[i]));
    open(energySite).forEach((r) => r.flush([energyBucket(1)]));
    open((r) => r.urlWithParams.includes('/analytics/carbon/trend'))
      .forEach((r) => r.flush([]));
    open((r) => r.urlWithParams.includes('/billing/costs'))
      .forEach((r) => r.flush([]));
    one(has('/intensity/current')).flush({});
    one(has('status=OPEN')).flush([]);
    one(has('status=ACKNOWLEDGED')).flush([]);

    const state = service.state();
    expect(state.status).toBe('ready');
    if (state.status !== 'ready') {
      return;
    }
    expect(state.data.consumptionPrevious).toHaveLength(1);
    // today 40 vs same-window yesterday 35 → +14.29%
    expect(state.data.kpis.todayConsumption.deltaPct).toBeCloseTo(14.29, 1);
  });
});
