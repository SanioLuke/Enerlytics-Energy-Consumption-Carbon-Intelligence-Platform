import { Injectable, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { type Observable, forkJoin, of } from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  map,
  shareReplay,
  startWith,
  switchMap,
} from 'rxjs/operators';
import { ApiClient } from '../../core/api/api-client';
import { ApiError } from '../../core/api/api-error';
import type {
  AggregationGranularity,
  AlertInstanceResponse,
  CarbonEmissionResponse,
  CarbonIntensityResponse,
  CostBucketResponse,
  EnergyAggregateResponse,
  PageResponse,
  SiteResponse,
} from '../../core/api/contracts';
import { AuthService } from '../../core/auth/auth.service';
import { type ContextPeriod, ContextService } from '../../core/context/context.service';
import { Subject } from 'rxjs';
import type {
  DashboardData,
  DashboardKpis,
  DashboardQuery,
  DashboardSection,
  DashboardState,
  SectionResult,
  SiteEnergySlice,
} from './dashboard.models';

/** Cap on per-site series queries for the comparison chart. */
const MAX_SITE_SLICES = 12;
const DAY_MS = 86_400_000;
const INITIAL_STATE: DashboardState = { status: 'idle' };

export interface ResolvedRange {
  from: string;
  to: string;
  granularity: AggregationGranularity;
}

/**
 * Cohesive dashboard query/state model.
 *
 * One DashboardQuery is derived from the global context (org, scope, period,
 * custom range, comparison). A single switchMap issues the full request batch
 * — analytics, carbon, billing, alerts, facilities — so a filter change or
 * refresh cancels stale in-flight work automatically. Each section is
 * error-isolated: a 403/500 on one dataset degrades that card, not the page.
 *
 * No manual subscriptions: state is a signal produced by toSignal, so there is
 * nothing to leak.
 */
@Injectable()
export class DashboardDataService {
  private readonly api = inject(ApiClient);
  private readonly auth = inject(AuthService);
  private readonly context = inject(ContextService);

  private readonly reload = new Subject<void>();

  /** Resolved query — null until an organization is active. */
  readonly query = computed<DashboardQuery | null>(() => {
    const organizationId = this.context.organizationId();
    if (!organizationId) {
      return null;
    }
    const siteId = this.context.scope().siteId;
    const range = resolveRange(
      this.context.period(),
      this.context.customFrom(),
      this.context.customTo(),
      new Date(),
    );
    const compare = this.context.comparison() === 'PRIOR_PERIOD';
    const durationMs = Date.parse(range.to) - Date.parse(range.from);

    return {
      organizationId,
      siteId,
      dimension: siteId ? 'SITE' : 'ORGANIZATION',
      dimensionId: siteId ?? organizationId,
      from: range.from,
      to: range.to,
      previousFrom: compare
        ? new Date(Date.parse(range.from) - durationMs).toISOString()
        : null,
      previousTo: compare ? range.from : null,
      granularity: range.granularity,
      compare,
    };
  });

  /**
   * Page state — switchMap cancels superseded loads; every section error is
   * captured into the data payload so the page always reaches 'ready'.
   */
  readonly state = toSignal(
    toObservable(this.query).pipe(
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      switchMap((query) => {
        if (!query) {
          return of<DashboardState>({ status: 'idle' });
        }
        return this.reload.pipe(startWith(void 0)).pipe(
          switchMap(() =>
            this.load(query).pipe(
              map(
                (data): DashboardState => ({
                  status: 'ready',
                  data,
                  query,
                }),
              ),
              startWith<DashboardState>({ status: 'loading' }),
            ),
          ),
        );
      }),
      // shareReplay so multiple template reads share one state
      shareReplay({ bufferSize: 1, refCount: true }),
    ),
    { initialValue: INITIAL_STATE },
  );

  refresh(): void {
    this.reload.next();
  }

  // -------------------------------------------------------------------------
  // Request batch — one cohesive load, per-section error isolation
  // -------------------------------------------------------------------------

  private load(query: DashboardQuery): Observable<DashboardData> {
    const base = this.api.orgPath(query.organizationId, '');

    return this.safe(
      this.api.get<PageResponse<SiteResponse>>(`${base}/sites`, {
        size: 100,
        sort: 'name,asc',
      }),
    ).pipe(
      switchMap((sitesResult) => {
        const sites =
          sitesResult.data?.content.filter((s) => s.active) ?? [];
        const intensitySiteId = query.siteId ?? sites[0]?.id ?? null;
        const today = todayRange();
        const yesterdayWindow = yesterdayWindowRange();

        const requests = {
          energy: this.safe(this.energySeries(base, query, query.from, query.to)),
          energyPrevious: query.compare
            ? this.safe(
                this.energySeries(
                  base,
                  query,
                  query.previousFrom!,
                  query.previousTo!,
                ),
              )
            : of<SectionResult<EnergyAggregateResponse[]>>({}),
          energyToday: this.safe(
            this.energySeries(base, query, today.from, today.to, 'HOUR'),
          ),
          energyYesterday: query.compare
            ? this.safe(
                this.energySeries(
                  base,
                  query,
                  yesterdayWindow.from,
                  yesterdayWindow.to,
                  'HOUR',
                ),
              )
            : of<SectionResult<EnergyAggregateResponse[]>>({}),
          carbon: this.safe(
            this.carbonSeries(base, query, query.from, query.to),
          ),
          carbonPrevious: query.compare
            ? this.safe(
                this.carbonSeries(
                  base,
                  query,
                  query.previousFrom!,
                  query.previousTo!,
                ),
              )
            : of<SectionResult<CarbonEmissionResponse[]>>({}),
          carbonToday: this.safe(
            this.carbonSeries(base, query, today.from, today.to, 'DAY'),
          ),
          carbonYesterday: query.compare
            ? this.safe(
                this.carbonSeries(
                  base,
                  query,
                  yesterdayWindow.from,
                  yesterdayWindow.to,
                  'DAY',
                ),
              )
            : of<SectionResult<CarbonEmissionResponse[]>>({}),
          cost: this.safe(this.costSeries(base, query, query.from, query.to)),
          costPrevious: query.compare
            ? this.safe(
                this.costSeries(base, query, query.previousFrom!, query.previousTo!),
              )
            : of<SectionResult<CostBucketResponse[]>>({}),
          costToday: this.safe(
            this.costSeries(base, query, today.from, today.to, 'DAY'),
          ),
          costYesterday: query.compare
            ? this.safe(
                this.costSeries(
                  base,
                  query,
                  yesterdayWindow.from,
                  yesterdayWindow.to,
                  'DAY',
                ),
              )
            : of<SectionResult<CostBucketResponse[]>>({}),
          intensity: intensitySiteId
            ? this.safe(
                this.api.get<CarbonIntensityResponse>(
                  `${base}/analytics/carbon/intensity/current`,
                  { siteId: intensitySiteId },
                ),
              )
            : of<SectionResult<CarbonIntensityResponse>>({}),
          alertsOpen: this.safe(
            this.api.get<AlertInstanceResponse[]>(`${base}/alerts`, {
              status: 'OPEN',
            }),
          ),
          alertsAcknowledged: this.safe(
            this.api.get<AlertInstanceResponse[]>(`${base}/alerts`, {
              status: 'ACKNOWLEDGED',
            }),
          ),
          siteEnergy: query.siteId
            ? of<SectionResult<SiteEnergySlice[]>>({})
            : this.safe(
                this.siteEnergySlices(base, query, sites),
              ),
        };

        return forkJoin(requests).pipe(
          map((r) =>
            this.derive(query, sitesResult, sites, intensitySiteId, r),
          ),
        );
      }),
    );
  }

  private energySeries(
    base: string,
    query: DashboardQuery,
    from: string,
    to: string,
    granularity?: AggregationGranularity,
  ): Observable<EnergyAggregateResponse[]> {
    return this.api.get<EnergyAggregateResponse[]>(
      `${base}/analytics/energy`,
      {
        dimension: query.dimension,
        dimensionId: query.dimensionId,
        granularity: granularity ?? query.granularity,
        from,
        to,
      },
    );
  }

  private carbonSeries(
    base: string,
    query: DashboardQuery,
    from: string,
    to: string,
    granularity?: AggregationGranularity,
  ): Observable<CarbonEmissionResponse[]> {
    return this.api.get<CarbonEmissionResponse[]>(
      `${base}/analytics/carbon/trend`,
      {
        dimension: query.dimension,
        dimensionId: query.dimensionId,
        granularity: granularity ?? query.granularity,
        from,
        to,
      },
    );
  }

  private costSeries(
    base: string,
    query: DashboardQuery,
    from: string,
    to: string,
    granularity?: AggregationGranularity,
  ): Observable<CostBucketResponse[]> {
    return this.api.get<CostBucketResponse[]>(`${base}/billing/costs`, {
      dimension: query.dimension,
      dimensionId: query.dimensionId,
      granularity: granularity ?? query.granularity,
      from,
      to,
    });
  }

  /**
   * Energy by site — one DAY-bucketed series per active site (bounded by
   * MAX_SITE_SLICES). There is no org-level per-site energy breakdown
   * endpoint; batching here keeps the query model cohesive.
   */
  private siteEnergySlices(
    base: string,
    query: DashboardQuery,
    sites: SiteResponse[],
  ): Observable<SiteEnergySlice[]> {
    const slice = sites.slice(0, MAX_SITE_SLICES);
    if (slice.length === 0) {
      return of([]);
    }
    return forkJoin(
      slice.map((site) =>
        this.api
          .get<EnergyAggregateResponse[]>(`${base}/analytics/energy`, {
            dimension: 'SITE',
            dimensionId: site.id,
            granularity: 'DAY',
            from: query.from,
            to: query.to,
          })
          .pipe(
            map(
              (buckets): SiteEnergySlice => ({
                siteId: site.id,
                siteName: site.name,
                energyKwh: sum(buckets, (b) => b.energyConsumedKwh),
              }),
            ),
            catchError((): Observable<SiteEnergySlice> =>
              of({ siteId: site.id, siteName: site.name, energyKwh: null }),
            ),
          ),
      ),
    );
  }

  // -------------------------------------------------------------------------
  // Derivation — pure mapping from raw payloads to the view model
  // -------------------------------------------------------------------------

  private derive(
    query: DashboardQuery,
    sitesResult: SectionResult<PageResponse<SiteResponse>>,
    sites: SiteResponse[],
    intensitySiteId: string | null,
    r: {
      energy: SectionResult<EnergyAggregateResponse[]>;
      energyPrevious: SectionResult<EnergyAggregateResponse[]>;
      energyToday: SectionResult<EnergyAggregateResponse[]>;
      energyYesterday: SectionResult<EnergyAggregateResponse[]>;
      carbon: SectionResult<CarbonEmissionResponse[]>;
      carbonPrevious: SectionResult<CarbonEmissionResponse[]>;
      carbonToday: SectionResult<CarbonEmissionResponse[]>;
      carbonYesterday: SectionResult<CarbonEmissionResponse[]>;
      cost: SectionResult<CostBucketResponse[]>;
      costPrevious: SectionResult<CostBucketResponse[]>;
      costToday: SectionResult<CostBucketResponse[]>;
      costYesterday: SectionResult<CostBucketResponse[]>;
      intensity: SectionResult<CarbonIntensityResponse>;
      alertsOpen: SectionResult<AlertInstanceResponse[]>;
      alertsAcknowledged: SectionResult<AlertInstanceResponse[]>;
      siteEnergy: SectionResult<SiteEnergySlice[]>;
    },
  ): DashboardData {
    const sectionErrors: Partial<Record<DashboardSection, ApiError>> = {};
    const note = (section: DashboardSection, result: SectionResult<unknown>) => {
      if (result.error) {
        sectionErrors[section] = result.error;
      }
    };
    note('sites', sitesResult);
    note('consumption', r.energy);
    note('demand', r.energyToday);
    note('carbon', r.carbon);
    note('cost', r.cost);
    note('intensity', r.intensity);
    note('alerts', r.alertsOpen);
    note('siteEnergy', r.siteEnergy);

    const todayBuckets = r.energyToday.data ?? [];
    const todayCost = r.costToday.data?.[0];
    const intensitySite = sites.find((s) => s.id === intensitySiteId);
    const currency =
      todayCost?.currency ??
      r.cost.data?.find((b) => b.currency)?.currency ??
      intensitySite?.currency ??
      null;

    const kpis = this.deriveKpis(query, todayBuckets, todayCost, r);

    return {
      kpis,
      consumption: (r.energy.data ?? []).map((b) => ({
        bucketStart: b.bucketStart,
        value: b.energyConsumedKwh,
      })),
      consumptionPrevious: r.energyPrevious.data
        ? r.energyPrevious.data.map((b) => ({
            bucketStart: b.bucketStart,
            value: b.energyConsumedKwh,
          }))
        : null,
      demand: todayBuckets.map((b) => ({
        bucketStart: b.bucketStart,
        value: b.averagePowerKw ?? null,
        secondary: b.peakPowerKw ?? null,
      })),
      siteEnergy: (r.siteEnergy.data ?? [])
        .slice()
        .sort((a, b) => (b.energyKwh ?? 0) - (a.energyKwh ?? 0)),
      carbonTrend: r.carbon.data ?? [],
      carbonTrendPrevious: r.carbonPrevious.data ?? null,
      costTrend: r.cost.data ?? [],
      costTrendPrevious: r.costPrevious.data ?? null,
      intensity: r.intensity.data ?? null,
      alerts: {
        open: r.alertsOpen.data ?? [],
        acknowledged: r.alertsAcknowledged.data ?? [],
      },
      sites,
      sectionErrors,
      fetchedAt: new Date().toISOString(),
      displayTimezone: intensitySite?.timezone ?? 'UTC',
      currency,
    };
  }

  private deriveKpis(
    query: DashboardQuery,
    todayBuckets: EnergyAggregateResponse[],
    todayCost: CostBucketResponse | undefined,
    r: {
      energy: SectionResult<EnergyAggregateResponse[]>;
      energyYesterday: SectionResult<EnergyAggregateResponse[]>;
      carbonToday: SectionResult<CarbonEmissionResponse[]>;
      carbonYesterday: SectionResult<CarbonEmissionResponse[]>;
      costYesterday: SectionResult<CostBucketResponse[]>;
      intensity: SectionResult<CarbonIntensityResponse>;
      alertsOpen: SectionResult<AlertInstanceResponse[]>;
      alertsAcknowledged: SectionResult<AlertInstanceResponse[]>;
    },
  ): DashboardKpis {
    const latest = [...todayBuckets]
      .reverse()
      .find((b) => b.averagePowerKw != null || b.peakPowerKw != null);
    const todayConsumption = sum(todayBuckets, (b) => b.energyConsumedKwh);
    const completeness = mean(
      todayBuckets.map((b) => b.dataCompletenessPercentage ?? 100),
    );
    const todayCarbonKg = sum(
      r.carbonToday.data ?? [],
      (b) => b.emissionsKgCo2Eq ?? 0,
    );
    const openCount = r.alertsOpen.data?.length ?? 0;
    const ackCount = r.alertsAcknowledged.data?.length ?? 0;

    // Deltas compare today's value with the same elapsed window yesterday.
    const yesterdayKwh = r.energyYesterday.data
      ? sum(r.energyYesterday.data, (b) => b.energyConsumedKwh)
      : null;
    const yesterdayCarbonKg = r.carbonYesterday.data
      ? sum(r.carbonYesterday.data, (b) => b.emissionsKgCo2Eq ?? 0)
      : null;
    const yesterdayCost = r.costYesterday.data?.[0]?.totalCost ?? null;

    return {
      currentDemand: {
        value: latest?.averagePowerKw ?? null,
        unit: 'kW',
        decimals: 1,
        quality: latest ? 'VALID' : 'UNAVAILABLE',
        meta: latest?.peakPowerKw
          ? `peak ${formatNumber(latest.peakPowerKw, 1)} kW today`
          : undefined,
        link: '/analytics',
      },
      todayConsumption: {
        value: todayBuckets.length ? todayConsumption : null,
        unit: 'kWh',
        decimals: 0,
        quality:
          todayBuckets.length === 0
            ? 'UNAVAILABLE'
            : completeness < 90
              ? 'PARTIAL'
              : 'VALID',
        meta:
          completeness < 100 && todayBuckets.length
            ? `${formatNumber(completeness, 0)}% complete`
            : undefined,
        deltaPct: query.compare
          ? deltaPct(todayConsumption, yesterdayKwh)
          : null,
        link: '/analytics',
      },
      todayCost: {
        value: todayCost?.totalCost ?? null,
        unit: todayCost?.currency ?? '',
        decimals: 2,
        currency: todayCost?.currency ?? undefined,
        quality: !todayCost?.totalCost
          ? 'UNAVAILABLE'
          : todayCost.qualityStatus === 'PARTIAL'
            ? 'PARTIAL'
            : 'VALID',
        meta: todayCost?.currency ?? undefined,
        deltaPct: query.compare
          ? deltaPct(todayCost?.totalCost ?? null, yesterdayCost)
          : null,
        link: '/costs',
      },
      todayCarbon: {
        value: r.carbonToday.data?.length ? todayCarbonKg / 1000 : null,
        unit: 'tCO₂e',
        decimals: 2,
        quality: r.carbonToday.data?.length
          ? r.carbonToday.data.some((b) => b.qualityStatus === 'PARTIAL')
            ? 'PARTIAL'
            : 'VALID'
          : 'UNAVAILABLE',
        deltaPct: query.compare
          ? deltaPct(
              todayCarbonKg / 1000,
              yesterdayCarbonKg !== null ? yesterdayCarbonKg / 1000 : null,
            )
          : null,
        link: '/carbon',
      },
      gridIntensity: {
        value: r.intensity.data?.carbonIntensityGCo2EqPerKwh ?? null,
        unit: 'gCO₂e/kWh',
        decimals: 0,
        quality: r.intensity.data
          ? r.intensity.data.estimated
            ? 'ESTIMATED'
            : 'VALID'
          : 'UNAVAILABLE',
        zone: r.intensity.data?.zone,
        meta: r.intensity.data
          ? `${r.intensity.data.zone}${r.intensity.data.estimated ? ' · est' : ''}`
          : undefined,
        link: '/carbon',
      },
      renewablePct: {
        // No renewable-source tracking exists in the backend yet — the tile
        // renders an honest N/A rather than a fabricated percentage.
        value: null,
        unit: '%',
        decimals: 0,
        quality: 'UNAVAILABLE',
        meta: 'no renewable source configured',
        link: '/sustainability',
      },
      activeAlerts: {
        value: openCount + ackCount,
        unit: '',
        decimals: 0,
        quality: r.alertsOpen.error ? 'UNAVAILABLE' : 'VALID',
        meta: `${openCount} open · ${ackCount} acknowledged`,
        link: '/alerts',
      },
    };
  }

  private safe<T>(source: Observable<T>): Observable<SectionResult<T>> {
    return source.pipe(
      map((data): SectionResult<T> => ({ data })),
      catchError(
        (error: unknown): Observable<SectionResult<T>> =>
          of({
            error:
              error instanceof ApiError
                ? error
                : new ApiError({
                    status: -1,
                    code: 'UNKNOWN',
                    detail: 'Unexpected error loading this section.',
                  }),
          }),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Pure helpers (exported for unit tests)
// ---------------------------------------------------------------------------

/** Maps a context period to a concrete UTC range + sensible granularity. */
export function resolveRange(
  period: ContextPeriod,
  customFrom: string | null,
  customTo: string | null,
  now: Date,
): ResolvedRange {
  const endOfTodayUtc = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1),
  );
  const startOfTodayUtc = new Date(endOfTodayUtc.getTime() - DAY_MS);

  switch (period) {
    case 'YESTERDAY':
      return {
        from: new Date(startOfTodayUtc.getTime() - DAY_MS).toISOString(),
        to: startOfTodayUtc.toISOString(),
        granularity: 'HOUR',
      };
    case 'LAST_7_DAYS':
      return {
        from: new Date(now.getTime() - 7 * DAY_MS).toISOString(),
        to: now.toISOString(),
        granularity: 'DAY',
      };
    case 'LAST_30_DAYS':
      return {
        from: new Date(now.getTime() - 30 * DAY_MS).toISOString(),
        to: now.toISOString(),
        granularity: 'DAY',
      };
    case 'CUSTOM': {
      const from = customFrom ? Date.parse(customFrom + 'T00:00:00Z') : NaN;
      const to = customTo ? Date.parse(customTo + 'T00:00:00Z') + DAY_MS : NaN;
      const valid = Number.isFinite(from) && Number.isFinite(to) && to > from;
      const resolvedFrom = valid ? from : now.getTime() - DAY_MS;
      const resolvedTo = valid ? to : now.getTime();
      const spanDays = (resolvedTo - resolvedFrom) / DAY_MS;
      return {
        from: new Date(resolvedFrom).toISOString(),
        to: new Date(resolvedTo).toISOString(),
        granularity: spanDays <= 2 ? 'HOUR' : spanDays <= 120 ? 'DAY' : 'MONTH',
      };
    }
    case 'TODAY':
    default:
      return {
        from: startOfTodayUtc.toISOString(),
        to: now.toISOString(),
        granularity: 'HOUR',
      };
  }
}

export function todayRange(): { from: string; to: string } {
  const now = new Date();
  const start = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()),
  );
  return { from: start.toISOString(), to: now.toISOString() };
}

/**
 * Yesterday's window covering the same elapsed duration as today so far —
 * the honest comparison basis for "today" KPI deltas.
 */
export function yesterdayWindowRange(): { from: string; to: string } {
  const now = new Date();
  const yesterday = new Date(now.getTime() - DAY_MS);
  const start = new Date(
    Date.UTC(
      yesterday.getUTCFullYear(),
      yesterday.getUTCMonth(),
      yesterday.getUTCDate(),
    ),
  );
  return { from: start.toISOString(), to: yesterday.toISOString() };
}

export function sum<T>(items: T[], pick: (item: T) => number | null | undefined): number {
  let total = 0;
  for (const item of items) {
    total += pick(item) ?? 0;
  }
  return total;
}

function mean(values: number[]): number {
  return values.length ? values.reduce((a, b) => a + b, 0) / values.length : 100;
}

function deltaPct(current: number | null | undefined, previous: number | null): number | null {
  if (current == null || previous == null || previous === 0) {
    return null;
  }
  return ((current - previous) / Math.abs(previous)) * 100;
}

export function formatNumber(value: number, decimals = 0): string {
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}
