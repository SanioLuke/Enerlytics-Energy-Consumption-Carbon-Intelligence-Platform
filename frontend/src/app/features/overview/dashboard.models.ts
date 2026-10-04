import type { ApiError } from '../../core/api/api-error';
import type {
  AggregationGranularity,
  AlertInstanceResponse,
  CarbonEmissionResponse,
  CarbonIntensityResponse,
  CostBucketResponse,
  DimensionType,
  SiteResponse,
} from '../../core/api/contracts';

/**
 * The cohesive dashboard query — every panel derives from one resolved query
 * so filters, comparison, and refresh stay consistent across the whole page.
 */
export interface DashboardQuery {
  organizationId: string;
  siteId: string | null;
  /** Analytics dimension and its id (ORGANIZATION→orgId, SITE→siteId). */
  dimension: DimensionType;
  dimensionId: string;
  /** UTC ISO instants for the analytics range (to is exclusive). */
  from: string;
  to: string;
  /** Previous equal-length range — only when comparison is enabled. */
  previousFrom: string | null;
  previousTo: string | null;
  granularity: AggregationGranularity;
  compare: boolean;
}

/** Per-section failure isolation: one panel may fail without sinking the page. */
export interface SectionResult<T> {
  data?: T;
  error?: ApiError;
}

export type QualityFlag = 'VALID' | 'ESTIMATED' | 'PARTIAL' | 'UNAVAILABLE';

export interface KpiValue {
  /** Formatted numeric part, or null when the value is unavailable. */
  value: number | null;
  unit: string;
  /** e.g. "%" formatting handled by the tile; decimals chosen by caller. */
  decimals: number;
  quality: QualityFlag;
  /** Secondary context line (e.g. "peak 412 kW at 14:00"). */
  meta?: string;
  /** Delta vs previous period, null when comparison is off or unknown. */
  deltaPct?: number | null;
  /** Where the tile deep-links on click. */
  link?: string;
}

export interface DashboardKpis {
  currentDemand: KpiValue;
  todayConsumption: KpiValue;
  todayCost: KpiValue & { currency?: string };
  todayCarbon: KpiValue;
  gridIntensity: KpiValue & { zone?: string };
  renewablePct: KpiValue;
  activeAlerts: KpiValue;
}

export interface TimeSeriesPoint {
  /** UTC ISO bucket start — axis formatting happens at render time. */
  bucketStart: string;
  value: number | null;
  /** Secondary series value (e.g. peak kW on the demand curve). */
  secondary?: number | null;
}

export interface SiteEnergySlice {
  siteId: string;
  siteName: string;
  energyKwh: number | null;
}

export interface AlertBadge {
  open: AlertInstanceResponse[];
  acknowledged: AlertInstanceResponse[];
}

export interface DashboardData {
  kpis: DashboardKpis;
  /** Consumption trend — current period and optional prior-period overlay. */
  consumption: TimeSeriesPoint[];
  consumptionPrevious: TimeSeriesPoint[] | null;
  /** Today's hourly demand (avg kW) and peak kW. */
  demand: TimeSeriesPoint[];
  siteEnergy: SiteEnergySlice[];
  carbonTrend: CarbonEmissionResponse[];
  carbonTrendPrevious: CarbonEmissionResponse[] | null;
  costTrend: CostBucketResponse[];
  costTrendPrevious: CostBucketResponse[] | null;
  intensity: CarbonIntensityResponse | null;
  alerts: AlertBadge;
  sites: SiteResponse[];
  /** Section failures — cards render their own error state per section. */
  sectionErrors: Partial<Record<DashboardSection, ApiError>>;
  /** ISO instant the dashboard data was fetched. */
  fetchedAt: string;
  /** Display timezone (selected site's IANA zone, else UTC). */
  displayTimezone: string;
  currency: string | null;
}

export type DashboardSection =
  | 'kpis'
  | 'consumption'
  | 'demand'
  | 'siteEnergy'
  | 'carbon'
  | 'cost'
  | 'intensity'
  | 'alerts'
  | 'sites';

export type DashboardState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'ready'; data: DashboardData; query: DashboardQuery };
