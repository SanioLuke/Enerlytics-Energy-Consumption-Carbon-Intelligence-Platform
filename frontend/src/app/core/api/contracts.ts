/**
 * API contract types for the Enerlytics backend.
 *
 * These interfaces are hand-maintained from contracts/openapi/*.yaml and the
 * backend DTO records under backend/src/main/java/com/enerlytics/**&#47;api/dto.
 * When the generated OpenAPI schema is published, these can be replaced with
 * generated types without touching call sites.
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
// Shared domain enums (API boundary values)
// ---------------------------------------------------------------------------

export type Granularity = 'HOUR' | 'DAY' | 'MONTH';
export type DimensionType = 'ORGANIZATION' | 'SITE' | 'BUILDING' | 'METER';
export type DataQualityStatus =
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

// ---------------------------------------------------------------------------
// Facilities (contracts/openapi/facilities.yaml)
// ---------------------------------------------------------------------------

export interface SiteResponse {
  id: string;
  organizationId: string;
  name: string;
  code: string;
  address?: string;
  ianaTimezone: string;
  currency: string;
  active: boolean;
}

export interface BuildingResponse {
  id: string;
  siteId: string;
  name: string;
  code: string;
  floorAreaSquareMeters?: number;
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
// Analytics / energy
// ---------------------------------------------------------------------------

export interface EnergyAggregateResponse {
  dimensionType: DimensionType;
  dimensionId: string;
  granularity: Granularity;
  bucketStart: string;
  bucketEnd: string;
  totalEnergyKwh: string;
  peakPowerKw?: string;
  avgPowerKw?: string;
  readingCount: number;
  completenessPct: string;
  estimated: boolean;
  qualityStatus: DataQualityStatus;
}

// ---------------------------------------------------------------------------
// Carbon
// ---------------------------------------------------------------------------

export interface CarbonEmissionResponse {
  dimensionType: DimensionType;
  dimensionId: string;
  granularity: Granularity;
  bucketStart: string;
  emissionsKgco2eq?: string;
  emissionsGco2eq?: string;
  emissionsTco2eq?: string;
  intensityGco2eqPerKwh?: string;
  coveredEnergyKwh?: string;
  coverageRatio?: string;
  gridRegionCode?: string;
  estimated: boolean;
  qualityStatus: DataQualityStatus;
}

export interface CurrentIntensityResponse {
  gridRegionCode: string;
  intensityGco2eqPerKwh?: string;
  observedAt?: string;
  source?: string;
  estimated: boolean;
  qualityStatus: DataQualityStatus;
}

// ---------------------------------------------------------------------------
// Billing / costs
// ---------------------------------------------------------------------------

export interface EnergyCostResponse {
  dimensionType: DimensionType;
  dimensionId: string;
  granularity: Granularity;
  bucketStart: string;
  energyCost?: string;
  demandCost?: string;
  totalCost?: string;
  currency?: string;
  billedEnergyKwh?: string;
  coverageRatio?: string;
  qualityStatus: DataQualityStatus;
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
// Alerts
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
  ruleId: string;
  alertType: string;
  severity: AlertSeverity;
  status: AlertStatus;
  scopeType: DimensionType;
  scopeId?: string;
  triggeredAt: string;
  acknowledgedAt?: string;
  acknowledgedBy?: string;
  resolvedAt?: string;
  resolvedBy?: string;
  context?: Record<string, unknown>;
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
