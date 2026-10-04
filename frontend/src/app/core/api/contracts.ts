/**
 * API contract types for the Enerlytics backend.
 *
 * These interfaces are hand-maintained from contracts/openapi/*.yaml and the
 * backend DTO records under backend/src/main/java/com/enerlytics/**&#47;api/dto.
 * When the generated OpenAPI schema is published, these can be replaced with
 * generated types without touching call sites.
 *
 * Note: backend BigDecimal fields serialize as JSON numbers.
 */

// ---------------------------------------------------------------------------
// Identity (contracts/openapi/identity.yaml)
// ---------------------------------------------------------------------------

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  defaultOrganizationId: string;
}

export type UserStatus = 'ACTIVE' | 'INVITED' | 'SUSPENDED' | 'DISABLED';

export interface OrganizationMembership {
  organizationId: string;
  organizationKey: string;
  organizationName: string;
  roles: string[];
  permissions: string[];
}

export interface UserInfoResponse {
  id: string;
  email: string;
  displayName: string;
  status: UserStatus;
  organizations: OrganizationMembership[];
}

// ---------------------------------------------------------------------------
// Shared envelopes and enums (API boundary values)
// ---------------------------------------------------------------------------

/** Mirrors common/api/PageResponse.java — paged list envelope. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type AggregationGranularity = 'HOUR' | 'DAY' | 'MONTH';
export type DimensionType =
  | 'ORGANIZATION'
  | 'SITE'
  | 'BUILDING'
  | 'ZONE'
  | 'METER';
export type QualityStatus =
  | 'VALID'
  | 'ESTIMATED'
  | 'PARTIAL'
  | 'MISSING'
  | 'LATE'
  | 'UNAVAILABLE';
export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type ForecastMethod =
  | 'SEASONAL_MOVING_AVERAGE'
  | 'SAME_HOUR_BASELINE'
  | 'TREND_ADJUSTED'
  | 'EXTERNAL_MODEL';
export type ForecastHorizon = 'NEXT_24_HOURS' | 'NEXT_7_DAYS';
export type TariffType = 'FLAT_RATE' | 'TIME_OF_USE';
export type AnomalyMethod =
  | 'ROLLING_MEAN_DEVIATION'
  | 'ROLLING_Z_SCORE'
  | 'SAME_HOUR_BASELINE'
  | 'PERCENTAGE_DEVIATION';
export type AnomalySeverity = 'LOW' | 'MEDIUM' | 'HIGH';
export type MeterStatus = 'ONLINE' | 'OFFLINE' | 'DEGRADED' | 'INACTIVE';
export type FloorAreaUnit = 'SQUARE_METERS' | 'SQUARE_FEET';

// ---------------------------------------------------------------------------
// Facilities (contracts/openapi/facilities.yaml)
// ---------------------------------------------------------------------------

export interface SiteResponse {
  id: string;
  organizationId: string;
  code: string;
  name: string;
  description?: string;
  address?: string;
  country?: string;
  state?: string;
  city?: string;
  postalCode?: string;
  latitude?: number;
  longitude?: number;
  timezone?: string;
  gridRegionCode?: string;
  currency?: string;
  floorArea?: number;
  floorAreaUnit?: FloorAreaUnit;
  active: boolean;
  openedOn?: string;
  closedOn?: string;
  createdAt: string;
  updatedAt: string;
  version?: number;
}

export interface BuildingResponse {
  id: string;
  siteId: string;
  organizationId: string;
  code: string;
  name: string;
  floorArea?: number;
  floorAreaUnit?: FloorAreaUnit;
  active: boolean;
}

// ---------------------------------------------------------------------------
// Meters (contracts/openapi/meters.yaml)
// ---------------------------------------------------------------------------

export interface MeterResponse {
  id: string;
  organizationId: string;
  siteId: string;
  buildingId?: string;
  name: string;
  serialNumber: string;
  channelType?: string;
  status: MeterStatus;
  lastReadingAt?: string;
  active: boolean;
}

// ---------------------------------------------------------------------------
// Analytics / energy (analytics/api/dto/EnergyAggregateResponse)
// ---------------------------------------------------------------------------

export interface EnergyAggregateResponse {
  dimension: DimensionType;
  dimensionId: string;
  granularity: AggregationGranularity;
  bucketStart: string;
  bucketEnd: string;
  energyConsumedKwh: number;
  averagePowerKw?: number;
  peakPowerKw?: number;
  minimumPowerKw?: number;
  averagePowerFactor?: number;
  readingCount: number;
  estimatedReadingCount: number;
  dataCompletenessPercentage?: number;
  computedAt?: string;
}

// ---------------------------------------------------------------------------
// Carbon (carbon/api/dto)
// ---------------------------------------------------------------------------

export interface CarbonIntensityResponse {
  zone: string;
  carbonIntensityGCo2EqPerKwh: number;
  estimated: boolean;
  retrievedAt: string;
}

export interface CarbonEmissionResponse {
  organizationId: string;
  dimension: DimensionType;
  dimensionId: string;
  granularity: AggregationGranularity;
  bucketStart: string;
  bucketEnd: string;
  gridRegionCode?: string;
  energyConsumedKwh?: number;
  carbonIntensityGCo2EqPerKwh?: number;
  carbonIntensitySource?: string;
  emissionsGCo2Eq?: number;
  emissionsKgCo2Eq?: number;
  emissionsTCo2Eq?: number;
  coverageRatio?: number;
  estimated: boolean;
  qualityStatus?: string;
}

export interface SiteComparisonResponse {
  siteId: string;
  siteName: string;
  gridRegionCode?: string;
  energyConsumedKwh?: number;
  carbonIntensityGCo2EqPerKwh?: number;
  emissionsKgCo2Eq?: number;
}

// ---------------------------------------------------------------------------
// Billing / costs (billing/api/dto)
// ---------------------------------------------------------------------------

export interface CostBucketResponse {
  organizationId: string;
  dimension: DimensionType;
  dimensionId: string;
  granularity: AggregationGranularity;
  bucketStart: string;
  bucketEnd: string;
  currency?: string;
  energyConsumedKwh?: number;
  energyCost?: number;
  demandCharge?: number;
  totalCost?: number;
  coverageRatio?: number;
  qualityStatus?: string;
  missingRateHours: number;
}

export interface BaselineCostResponse {
  currency?: string;
  currentFrom: string;
  currentTo: string;
  baselineFrom: string;
  baselineTo: string;
  currentTotalCost?: number;
  baselineTotalCost?: number;
  deltaCost?: number;
  deltaPct?: number;
}

export interface TariffResponse {
  id: string;
  siteId: string;
  name: string;
  tariffType: TariffType;
  currency: string;
  ianaTimezone: string;
  effectiveFrom: string;
  effectiveTo?: string;
  active: boolean;
}

// ---------------------------------------------------------------------------
// Forecast
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Live energy monitoring (SSE snapshots)
// ---------------------------------------------------------------------------

export interface LiveEnergySnapshot {
  generatedAt: string;
  organizationId: string;
  siteId?: string;
  buildingId?: string;
  currentDemandKw: number;
  activeMeterCount: number;
  offlineMeterCount: number;
  meterReadings: MeterLiveReading[];
  recentTrend: TrendPoint[];
}

export interface MeterLiveReading {
  meterId: string;
  meterName?: string;
  currentPowerKw: number;
  lastSeenAt: string;
  status: 'ONLINE' | 'OFFLINE';
}

export interface TrendPoint {
  timestamp: string;
  demandKw: number;
}

export interface ForecastPointResponse {
  timestamp: string;
  predictedKwh: string;
  lowerBoundKwh?: string;
  upperBoundKwh?: string;
  method: ForecastMethod;
  generatedAt: string;
  explanation?: string;
  actualKwh?: string;
  absoluteError?: string;
  pctError?: string;
}

export interface ForecastRunResponse {
  id: string;
  dimensionType: DimensionType;
  dimensionId: string;
  horizon: ForecastHorizon;
  method: ForecastMethod;
  generatedAt: string;
  points: ForecastPointResponse[];
  maeKwh?: string;
  mapePct?: string;
  evaluatedPoints: number;
}

// ---------------------------------------------------------------------------
// Alerts (alert/api/dto)
// ---------------------------------------------------------------------------

export interface AlertRuleResponse {
  id: string;
  alertType: string;
  scopeType: DimensionType;
  scopeId?: string;
  metric: string;
  operator: string;
  threshold: string;
  evaluationWindowMinutes: number;
  severity: AlertSeverity;
  cooldownMinutes: number;
  enabled: boolean;
  lastEvaluatedAt?: string;
  lastTriggeredAt?: string;
}

export interface AlertInstanceResponse {
  id: string;
  organizationId: string;
  ruleId: string;
  ruleName?: string;
  scopeType: DimensionType;
  scopeId?: string;
  alertType: string;
  metric?: string;
  observedValue?: number;
  threshold?: number;
  severity: string;
  status: string;
  triggeredAt: string;
  acknowledgedAt?: string;
  acknowledgedBy?: string;
  resolvedAt?: string;
  resolvedBy?: string;
  contextPayload?: string;
}

// ---------------------------------------------------------------------------
// Anomalies
// ---------------------------------------------------------------------------

export interface AnomalyResponse {
  id: string;
  dimensionType: DimensionType;
  dimensionId: string;
  detectedAt: string;
  actualKwh: string;
  expectedKwh: string;
  deviationPct: string;
  method: AnomalyMethod;
  confidence: string;
  severity: AnomalySeverity;
  explanation: string;
}
