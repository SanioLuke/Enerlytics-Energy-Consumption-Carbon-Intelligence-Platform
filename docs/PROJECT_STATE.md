# Enerlytics Project State

## Current Phase

Phase 20 — Real-time energy monitoring (Live Energy) implemented end-to-end.
The backend consumes validated telemetry internally (Spring application events,
not raw Kafka in the browser), maintains a bounded in-memory live state, and
publishes sanitized snapshots over SSE with heartbeat-like periodic emission
and a REST fallback endpoint. The frontend adds a dedicated Live Energy page
with current demand, active/offline meter counts, live trend chart, and
site/building/meter filters. SSE updates are coalesced to the screen refresh
cadence, and the connection auto-reconnects with exponential backoff before
falling back to snapshot polling. Verified by lint, 50 frontend unit tests,
151 backend tests, and an Angular production build.

The live monitoring layer delivers:

- **Backend state engine** — `LiveEnergyService` receives
  `MeterReadingValidatedEvent` from the existing telemetry ingestion pipeline
  and updates per-meter state (power, last seen, online/offline) plus per-scope
  demand and bounded trend history. Snapshots are coalesced to a configurable
  publish interval (default 1s), so browsers receive at most one update per
  interval regardless of telemetry rate.
- **Tenant-scoped SSE contract** — `GET
  /api/v1/organizations/{orgId}/live/energy/subscribe` streams `snapshot` events;
  `GET .../snapshot` is a REST fallback. Filters are optional `siteId` and
  `buildingId` query params. Because browsers cannot set headers on
  `EventSource`, the stream passes the JWT access token as an `access_token`
  query parameter, which `JwtAuthenticationFilter` resolves alongside the
  standard `Authorization` header. The DTO includes `currentDemandKw`,
  `activeMeterCount`, `offlineMeterCount`, `meterReadings`, and `recentTrend`
  with no internal Kafka topic or raw telemetry field exposed.
- **Offline detection** — meters move to offline when no validated reading has
  arrived within the configured threshold (default 5 minutes); scope demand and
  counts are recalculated on the next publish cycle.
- **Bounded memory** — per-scope trend windows are capped by `trendMaxPoints`
  (default 360); stale meter records are not persisted; SSE emitters are removed
  on completion/timeout/error.
- **Frontend live service** — `LiveEnergyService` wraps `EventSource` with
  reconnect logic (exponential backoff 1s–30s, max 5 attempts) and transparently
  switches to polling the snapshot endpoint if SSE remains unavailable. UI updates
  are throttled via `sampleTime(500ms)` so high-frequency telemetry cannot
  trigger excessive Angular change detection.
- **Live Energy page** — filter bar (site/building/meter), status indicator,
  KPI strip with current demand / active meters / offline meters / selected
  meter power, a real-time trend chart, and a meter readings panel with
  status badges and last-seen timestamps. Empty/loading/error states reuse the
  existing reusable state components.
- **Tests** — backend tests cover empty snapshots, active/offline transitions,
  aggregated demand, trend bounding, and the coalesced publish cycle. Frontend
  tests cover EventSource connection, snapshot parsing, reconnect/backoff,
  fallback polling, filter-scoped URLs, and meter-selection filtering.

### Phase 19 — Executive Overview dashboard (prior phase)

Phase 19 — Executive Overview dashboard implemented against the real backend
analytics, carbon, billing, facilities, and alert APIs on the Phase 18
foundation. The page is wired to a cohesive dashboard query model (no
per-component fetching), renders the seven required KPI tiles and the §4.2
visualizations with Apache ECharts, and is verified by lint, 43 unit tests,
and a production build. Remaining business pages still render the shared
placeholder.

The dashboard delivers:

- **Cohesive query/state model** — `DashboardDataService` resolves one
  `DashboardQuery` from the global context (organization, site scope, period,
  custom range, prior-period comparison) and issues the whole request batch
  inside a single `switchMap` (`forkJoin` per section): energy aggregates,
  carbon trend, cost buckets, current intensity, alert lists, sites, and the
  bounded per-site energy fan-out for the comparison chart. Filter changes
  and refresh cancel stale in-flight work; state is a signal via `toSignal`,
  so there are no manual subscriptions to leak. Sections are error-isolated
  — a failed carbon call degrades that card, not the page.
- **KPI tiles** — Current Demand (kW, latest hourly average with peak meta),
  Today's Consumption (kWh with completeness marks), Today's Cost (currency
  resolved from the cost bucket or site), Today's Carbon (tCO₂e), Current
  Grid Carbon Intensity (gCO₂e/kWh with zone + estimated marks), Renewable
  Energy (honest `N/A` — no backend source exists yet), and Active Alerts
  (open + acknowledged count). Comparison mode shows deltas versus the same
  elapsed window yesterday.
- **Charts** — Apache ECharts (tree-shaken `echarts/core`, dynamically
  imported into the lazy overview chunk): consumption-over-time line with
  optional dashed prior-period overlay, today hourly demand curve
  (average + peak), energy-by-site horizontal bars (org scope only), carbon
  emissions trend, cost trend, plus a recent-alerts severity feed and a
  sustainability-target card that renders an explicit "not yet modeled"
  empty state instead of a fabricated value. Axes and tooltips carry
  explicit units; `aria` descriptions are enabled; buckets with no data
  render as gaps, never zero.
- **Filter bar** — organization, site, period presets (today / yesterday /
  last 7 / last 30 days), custom date range, previous-period comparison
  toggle, manual refresh, and the required `Last updated HH:MM:SS UTC`
  indicator. State persists through `ContextService` into URL query params
  (shareable) with localStorage fallback; custom range extends `ContextService`
  with `from`/`to` params.
- **States and a11y** — skeleton loading, per-card API error states with
  retry, empty states for missing data, 7→4→scroll responsive KPI strip,
  12-column chart grid collapsing to single column, labelled controls,
  `role="img"` charts with aria labels, and a 60-second auto-refresh
  bounded by `takeUntilDestroyed`.
- **Contracts fix** — `ApiClient.orgPath(orgId, '')` no longer emits a
  trailing slash (previously produced `//sites`-style URLs that Spring
  would not normalize).

### Phase 18 — Angular application foundation (prior phase)

Phase 18 implemented the Angular application foundation per `docs/UX_SPEC.md` —
shell, design system, security plumbing, and state components — verified by
lint, unit tests, and a production build.

The foundation delivers:

- **Design tokens** — `src/styles/_tokens.scss` mirrors every `--ely-*`
  custom property from UX_SPEC §3 (surfaces, warm-neutral text, deep-teal
  accent, semantic status palette, 8-color chart palette, typography scale,
  4px spacing grid, ≤8px radii, float-only shadow, layout widths,
  breakpoints). Angular Material system tokens are remapped to the Enerlytics
  palette so Material is used selectively (menus, tooltips) without imposing
  default Material styling. Inter replaces Roboto.
- **Centralized HTTP** — `ApiClient` prefixes the environment `apiBaseUrl`,
  serializes query params, and provides the tenant-scoped `orgPath()` helper.
  `apiErrorInterceptor` normalizes every failure into `ApiError` (RFC 9457
  `Problem` aware: status, code, detail, correlationId, field errors).
- **Authentication** — `AuthService` holds user/active-organization/
  permissions as signals; `TokenStorage` persists tokens; `authInterceptor`
  attaches the bearer token and performs a single-flight refresh + retry on
  401, dropping to `/login` when refresh fails. Session restore is
  idempotent so guards do not re-probe `/me` per navigation.
- **Route guards** — `authGuard`, `permissionGuard` (any-of permissions from
  route `data`, fails closed to `/access-denied`), and `guestGuard`.
- **RBAC directive** — `*appHasAuthority` structural directive with
  `appHasAuthorityAll` for all-of semantics; permissions derive from the
  active organization membership.
- **Reusable states** — `LoadingStateComponent` (skeleton variants:
  card/table/kpi-strip/page), `EmptyStateComponent`, `ErrorStateComponent`
  (RFC 9457 detail + copyable correlation ID + retry).
- **Responsive shell** — 232px nav rail (64px icon rail at ≤1023px, drawer at
  ≤767px), permission-filtered nav groups (Monitor/Analyze/Manage/Govern),
  top bar with route-driven breadcrumbs and profile menu with organization
  switcher, 1560px content region.
- **Global context** — `ContextService` holds org/site/building scope,
  period, granularity, and comparison; state serializes to URL query params
  (shareable) with localStorage fallback per the §4.2 filter contract.
- **API contracts** — `core/api/contracts.ts` types the identity, facilities,
  meters, energy, carbon, billing, forecast, alert, and anomaly DTOs from
  `contracts/openapi/*.yaml` and the backend records.
- **Routes** — `/login` (two-panel spec layout) plus lazy-loaded children
  under the shell for all 12 nav surfaces; unbuilt pages render a shared
  `PagePlaceholderComponent` driven by route data. Access-denied and
  not-found pages included.
- **Tooling** — strict TypeScript enabled; ESLint flat config
  (`angular-eslint` + `typescript-eslint`, `npm run lint`); Vitest unit
  tests via `@angular/build:unit-test`; `scripts/set-env.js` now writes both
  environment files and `angular.json` gained `fileReplacements` so the
  production build uses `environment.production.ts`.

### Phase 17 — UI/UX specification (prior phase)

Phase 17 produced `docs/UX_SPEC.md` — the information architecture, design
system, and wireframe-level executive dashboard specification the Angular
foundation now implements.

`docs/UX_SPEC.md` defines the full product design: a dense, analytical
workspace aesthetic (flat surfaces, hairline borders, single teal accent,
≤8px radii, no decorative gradients), a 12-item primary navigation grouped
into Monitor / Analyze / Manage / Govern sections, a global context model
(org → scope → period/granularity with site-timezone display), and a reusable
design system covering color tokens, typography, spacing, grid, cards, KPI
tiles, buttons, forms, tables, chips, charts vocabulary, status/severity/
data-quality indicators, and state patterns (empty/loading/error/stale).

Sixteen screens are specified end-to-end — login, executive overview, energy
dashboard, carbon dashboard, cost dashboard, live monitoring, site details,
building details, meter details, meter explorer, alerts center, forecasts,
reports, sustainability targets, organization settings, and user management —
each with user objective, layout, KPIs, charts, tables, filters,
interactions, drill-down, and empty/loading/error/responsive behavior.
Data-quality marks (VALID/ESTIMATED/MISSING/LATE/PARTIAL/UNAVAILABLE) are
first-class: missing values are never rendered as zero or interpolated.

The executive overview (§4.2) is specified to wireframe detail: ASCII
wireframes for desktop/tablet/mobile, the seven required KPI tiles (current
demand, today's consumption/cost/carbon, grid intensity, renewable %,
active alerts), ten required visualizations, the org/site/building/
period/comparison filter contract with URL persistence, and the interaction
contract (tooltips, brush zoom, click-to-drill, auto-refresh cadence,
explicit last-updated indicators, mandatory unit labels).

### Phase 16 — Energy consumption forecasting (prior phase)

Phase 16 completed deterministic energy forecasting. Forecasts are generated by
deterministic statistical providers behind a `ForecastProvider` SPI so an
external ML service can replace them later without changing orchestration,
persistence, or APIs (`ForecastMethod.EXTERNAL_MODEL` is reserved).

Three built-in methods cover both horizons (`NEXT_24_HOURS` hourly,
`NEXT_7_DAYS` daily): `SEASONAL_MOVING_AVERAGE` (mean of the same
hour-of-week/day-of-week position), `SAME_HOUR_BASELINE` (mean of the same
hour-of-day; flat daily mean for the daily horizon), and `TREND_ADJUSTED`
(seasonal mean plus least-squares daily slope multiplied by days-ahead from
the end of history). All math uses `BigDecimal` scale 9 `HALF_EVEN`; each point
carries a prediction interval of `predicted ± z×sigma` (fallback margin when
sigma cannot be computed) and a plain-language explanation.

Runs persist in `forecast.forecast_run`/`forecast.forecast_point` with the
required fields — timestamp, predictedKwh, lower/upper bounds, method, and
generatedAt — plus per-point explanation and evaluation columns. History is
drawn from canonical energy aggregates, excluding buckets below the
completeness threshold; a configurable minimum history requirement rejects
under-sampled entities instead of fabricating forecasts.

`POST .../forecasts/runs/{runId}/evaluate` backfills actual consumption for
forecast buckets whose aggregates now exist and computes exact MAE over all
evaluated points and MAPE only where actual exceeds the configured floor —
zero-actual buckets contribute to MAE but are excluded from MAPE.

### Phase 15 — Alert-rule engine (prior phase)

Phase 15 completed the alert-rule engine. Organizations can create configurable
alert rules scoped to `ORGANIZATION`, `SITE`, `BUILDING`, or `METER` with a
metric, operator (`GREATER_THAN`, `LESS_THAN`, `EQUALS`, `NOT_EQUALS`), threshold,
evaluation window, severity (`INFO`, `WARNING`, `CRITICAL`), cooldown, and
enabled status. The evaluator maps each `AlertType` (`HIGH_CONSUMPTION`,
`HIGH_DEMAND`, `CARBON_INTENSITY_HIGH`, `METER_OFFLINE`, `ABNORMAL_USAGE`,
`TARGET_EXCEEDED`) to a tenant-scoped metric: energy aggregates, carbon
emissions/intensity, meter heartbeat state, anomaly count, and sustainability
targets.

Duplicate alert storms are prevented by a per-rule cooldown. When an alert
instance is triggered, persisted, and published to the outbox, subsequent
evaluations within the cooldown window skip the rule. Alert instances carry a
lifecycle (`OPEN`, `ACKNOWLEDGED`, `RESOLVED`) with timestamps and actor details
for each transition, plus a JSON `contextPayload` capturing the triggering value,
threshold, window, and rule metadata.

Lifecycle APIs allow operators to acknowledge or resolve active alerts.
Outbox events (`AlertCreatedEvent`, `AlertAcknowledgedEvent`, `AlertResolvedEvent`)
are serialized and placed in the existing transactional outbox for reliable
publication to Kafka, enabling downstream notification services without
blocking the alert transaction.

### Phase 14 — Interpretable energy anomaly detection (prior phase)

Phase 14 completed interpretable anomaly detection. The anomaly module uses
deterministic statistical detectors rather than an LLM: trailing rolling mean
percentage deviation, rolling population z-score, prior-day same-hour baseline,
and historical same-hour mean across configurable prior days. All detectors
implement a common `AnomalyDetector` interface so future statistical or ML models
can be introduced without changing orchestration, persistence, or APIs.

Canonical anomaly records identify the entity and timestamp and persist actual
and expected kWh, signed deviation percentage, method, confidence, severity,
plain-language explanation, calculation run, and detection time. Detection runs
are replay-safe. History APIs exclude suppressed records.

False-alert controls exclude aggregates below the configured telemetry
completeness threshold, require contiguous rolling windows and minimum baseline
samples, suppress the configurable startup period from the entity's first
hourly aggregate, and suppress candidates covered by organization/entity
maintenance windows. Maintenance suppressions remain stored for auditability
but are not returned as active anomaly history.

### Phase 13 — Configurable electricity tariff engine (prior phase)

Phase 13 completed configurable electricity tariffs. Enerlytics can now
price energy consumption with `FLAT_RATE` and `TIME_OF_USE` tariffs assigned per
site. Tariffs carry currency, IANA timezone, and an effective date range;
overlapping effective ranges per site are rejected. Rate windows specify day
type (ALL/WEEKDAY/WEEKEND evaluated on the local date), start/end local times —
including midnight-wrapping windows such as 22:00-06:00 — cost per kWh, an
optional demand rate per kW, and a priority for overlapping windows.

`CostCalculationService` prices each meter-level hourly aggregate using the
tariff effective on the hour's local date and the rate matching the local time
and day type, then rolls results up to meter, building, site, and organization
dimensions at HOUR/DAY/MONTH granularities. Demand charge per bucket is the
maximum hourly `peakPowerKw × demandRatePerKw`. Results persist in
`billing.energy_cost` with exact decimal arithmetic (scale 9, `HALF_EVEN`),
currency, energy cost, demand charge, total, coverage, quality status, missing
rate hours, and calculation run id. Recalculation is idempotent via the bucket
unique key; late readings and tariff corrections recompute deterministically
under a new run id.

Buckets with no applicable tariff or rate are marked `UNAVAILABLE` with null
costs — missing pricing is never substituted with zero; partial coverage is
`PARTIAL` with an energy-weighted coverage ratio. Buckets priced under multiple
currencies are not summed; they are marked `UNAVAILABLE` with null currency.

### Phase 12D — Carbon emissions processing (prior phase)

Phase 12D completed carbon emissions processing. Enerlytics now computes
location-based Scope 2 emissions per `CALCULATION_SPEC.md`: for each meter-level
hourly energy aggregate, the energy consumed (kWh) is multiplied by the grid
carbon intensity (gCO2eq/kWh, converted exactly to kgCO2eq/kWh) of the meter's
site grid region, then rolled up deterministically to meter, building, site, and
organization dimensions at HOUR, DAY, and MONTH granularities.

Canonical values are persisted in `carbon.emission` with exact decimal
arithmetic (scale 9, `HALF_EVEN`), and all three exposure units — gCO2eq,
kgCO2eq, and tCO2eq — are derived and stored per bucket. Each row carries
provenance: grid region, provider/source, estimated flag, quality status,
energy-weighted coverage ratio, missing-factor hours, calculation run id, and
computation timestamp. A unique key on `(organization, dimension, granularity,
bucket_start)` makes recalculation idempotent: late readings and corrected
intensity observations update the same row under a new run id instead of
duplicating it.

Quality semantics follow the spec: missing or stale factors (older than 24h)
are never substituted with zero — buckets are marked `UNAVAILABLE` or `PARTIAL`
with explicit coverage; estimated provider values propagate `estimated=true`
and `ESTIMATED` quality; realized carbon intensity is always energy-weighted
(emissions ÷ covered energy), never an arithmetic mean.

## Completed Work

### Phase 0 — Documentation Foundation

- Established documentation, engineering rules, technology baseline, ADR process,
  repository structure, and initial conventions.

### Phase 1 — Product Architecture and Domain Analysis

- Defined the functional specification for six personas and 25 capabilities,
  including phased scope, permissions, journeys, validation, and acceptance
  criteria.

### Phase 2 — Technical Architecture Design

- Defined modular runtime, frontend/backend boundaries, telemetry worker, Kafka,
  SSE, observability, scaling, and extraction strategy.

### Phase 3 — PostgreSQL Data Model

- Designed logical/physical PostgreSQL schemas, tenant-safe keys, decimal
  precision, partitioning, indexes, rollups, retention, auditability, and
  migration strategy.

### Phase 4 — Event-Driven Telemetry Architecture

- Defined event contracts, topics, keys, ordering, idempotency, retries/DLT,
  outbox/inbox boundaries, late-data handling, replay, scaling, and observability.

### Phase 5 — Deterministic Calculation Specification

- Defined canonical units and exact conversion constants for energy, power, area,
  financial values, carbon emissions, and carbon intensity.
- Specified all 16 required deterministic calculations with inputs, output, unit,
  precision, rounding, failure behavior, and worked examples.

### Phase 6 — Repository Bootstrap

- Created backend, frontend, infrastructure, scripts, root metadata, and local
  Docker Compose services (PostgreSQL, Kafka, Redis) with health checks.

### Phase 7 — Identity and Access Control

- Implemented JWT authentication, refresh-token rotation, RBAC, organization-level
  tenant isolation, Flyway-managed identity schema, standardized errors, and
  integration tests.

### Phase 8 — Organization and Facility Domain

- Implemented CRUD and lifecycle for `Organization`, `Site`, `Building`, and `Zone`
  with DTOs, validation, pagination, sorting, search, tenant authorization, audit
  fields, and optimistic locking.

### Phase 9 — Meter Management

- Implemented registration, lifecycle, location assignment, search/filtering,
  heartbeat, and last-seen tracking for electricity meters with tenant authorization
  and tests.

### Phase 10 — Telemetry Simulator

- Added Flyway migration `V1_3_0__add_meter_simulation_columns.sql` to mark meters
  as simulated and store their simulation profile.
- Created a new Maven module `backend/telemetry-simulator` with its own
  Spring Boot application and configuration.
- Implemented `MeterSource` JDBC reader for active simulated meters.
- Implemented `LoadProfileCalculator` with profile-specific curves for OFFICE,
  DATA_CENTER, WAREHOUSE, RETAIL, MANUFACTURING, and RESIDENTIAL.
- Implemented `TelemetryGenerator` with deterministic event IDs, seeded randomness,
  energy/power/current/power-factor/frequency/quality generation, and anomaly
  injection.
- Implemented `TelemetryEventProducer` using Spring Kafka with idempotent producer
  settings.
- Implemented `SimulatorEngine` with tick-based virtual time, acceleration,
  per-meter scheduling, startup/shutdown lifecycle, and refresh of the active meter
  registry.
- Added Micrometer metrics (`telemetry.simulator.produced`,
  `telemetry.simulator.failed`, `telemetry.simulator.active_meters`).
- Added tests:
  - `LoadProfileCalculatorTest`
  - `TelemetryGeneratorTest`
  - `SimulatorEngineTest`
  - `TelemetryEventProducerTest`
- Updated `docs/EVENT_ARCHITECTURE.md` with the simulator's role, event fields,
  and configuration.

## Changed Files

### Phase 10

- `backend/src/main/resources/db/migration/V1_3_0__add_meter_simulation_columns.sql`
- `backend/telemetry-simulator/pom.xml`
- `backend/telemetry-simulator/src/main/resources/application.yml`
- `backend/telemetry-simulator/src/main/java/com/enerlytics/simulator/**`
- `backend/telemetry-simulator/src/test/java/com/enerlytics/simulator/**`
- `backend/src/main/java/com/enerlytics/meter/api/internal/SimulationInternalController.java`
- `backend/src/main/java/com/enerlytics/meter/api/dto/SimulatedMeterResponse.java`
- `backend/src/main/java/com/enerlytics/meter/application/SimulationService.java`
- `backend/src/main/java/com/enerlytics/meter/domain/MeterSimulationProfile.java`
- `backend/src/main/java/com/enerlytics/security/internal/InternalApiKeyAuthFilter.java`
- `backend/src/main/java/com/enerlytics/security/internal/InternalApiAuthentication.java`
- `backend/src/main/java/com/enerlytics/config/SecurityConfig.java`
- `backend/src/main/java/com/enerlytics/meter/infrastructure/persistence/MeterRepository.java`
- `backend/src/main/java/com/enerlytics/meter/application/MeterService.java`
- `backend/src/test/java/com/enerlytics/meter/MeterRepositoryTest.java`
- `backend/src/test/java/com/enerlytics/meter/MeterServiceTest.java`
- `docs/EVENT_ARCHITECTURE.md`
- `docs/PROJECT_STATE.md`
- `README.md`
- `.env.example`
- `.gitignore`

### Phase 11

- `backend/src/main/resources/db/migration/V1_4_0__telemetry_ingestion_schema.sql`
- `backend/src/main/java/com/enerlytics/telemetry/api/event/MeterReadingReceivedEvent.java`
- `backend/src/main/java/com/enerlytics/telemetry/api/event/MeterReadingValidatedEvent.java`
- `backend/src/main/java/com/enerlytics/telemetry/api/event/MeterReadingRejectedEvent.java`
- `backend/src/main/java/com/enerlytics/telemetry/api/kafka/TelemetryIngestionConsumer.java`
- `backend/src/main/java/com/enerlytics/telemetry/application/TelemetryValidator.java`
- `backend/src/main/java/com/enerlytics/telemetry/application/TelemetryValidationResult.java`
- `backend/src/main/java/com/enerlytics/telemetry/application/TelemetryIngestionService.java`
- `backend/src/main/java/com/enerlytics/telemetry/application/OutboxRelay.java`
- `backend/src/main/java/com/enerlytics/telemetry/domain/MeterReadingEntity.java`
- `backend/src/main/java/com/enerlytics/telemetry/domain/MeterReadingRejectedEntity.java`
- `backend/src/main/java/com/enerlytics/telemetry/domain/OutboxEntity.java`
- `backend/src/main/java/com/enerlytics/telemetry/infrastructure/persistence/MeterReadingRepository.java`
- `backend/src/main/java/com/enerlytics/telemetry/infrastructure/persistence/MeterReadingRejectedRepository.java`
- `backend/src/main/java/com/enerlytics/telemetry/infrastructure/persistence/OutboxRepository.java`
- `backend/src/main/java/com/enerlytics/config/TimeConfig.java`
- `backend/src/main/java/com/enerlytics/EnerlyticsBackendApplication.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/enerlytics/telemetry/application/TelemetryValidatorTest.java`
- `backend/src/test/java/com/enerlytics/telemetry/application/TelemetryIngestionServiceTest.java`
- `docs/EVENT_ARCHITECTURE.md`
- `docs/PROJECT_STATE.md`
- `.env.example`

### Phase 12A

- `backend/src/test/java/com/enerlytics/telemetry/api/kafka/TelemetryIngestionKafkaIntegrationTest.java`
- `backend/src/test/resources/application-integration.yml`
- `backend/src/main/java/com/enerlytics/config/KafkaConfig.java`
- `backend/pom.xml` (added `spring-kafka-test`, `awaitility`, `metrics-core`; removed `kafka.version` override)
- `docs/EVENT_ARCHITECTURE.md`
- `docs/PROJECT_STATE.md`

### Phase 12B

- `backend/src/main/resources/db/migration/V1_5_0__energy_aggregation_schema.sql`
- `backend/src/main/java/com/enerlytics/analytics/domain/**`
- `backend/src/main/java/com/enerlytics/analytics/application/**`
- `backend/src/main/java/com/enerlytics/analytics/infrastructure/persistence/**`
- `backend/src/main/java/com/enerlytics/analytics/api/**`
- `backend/src/test/java/com/enerlytics/analytics/EnergyAggregationServiceTest.java`
- `backend/src/test/java/com/enerlytics/analytics/EnergyAggregationConsumerTest.java`
- `backend/src/test/java/com/enerlytics/analytics/EnergyAnalyticsApiIntegrationTest.java`
- `backend/src/test/java/com/enerlytics/analytics/EnergyAggregationBenchmarkTest.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/resources/application-integration.yml`
- `.env.example`
- `docs/ENERGY_AGGREGATION.md`
- `docs/EVENT_ARCHITECTURE.md`
- `docs/PROJECT_STATE.md`

### Phase 12C

- `backend/src/main/resources/db/migration/V1_6_0__carbon_intensity_observation_schema.sql`
- `backend/src/main/java/com/enerlytics/carbon/provider/**`
- `backend/src/main/java/com/enerlytics/carbon/domain/**`
- `backend/src/main/java/com/enerlytics/carbon/infrastructure/persistence/**`
- `backend/src/main/java/com/enerlytics/carbon/config/**`
- `backend/src/test/java/com/enerlytics/carbon/provider/MockCarbonIntensityProviderTest.java`
- `backend/src/test/java/com/enerlytics/carbon/provider/ElectricityMapsCarbonIntensityProviderTest.java`
- `backend/src/test/java/com/enerlytics/carbon/infrastructure/persistence/CarbonIntensityObservationRepositoryTest.java`
- `backend/src/test/java/com/enerlytics/carbon/config/CarbonIntensityProviderSelectionTest.java`
- `backend/pom.xml` (added `spring-boot-starter-cache`, `caffeine`, `resilience4j-spring-boot3`, `mockwebserver`)
- `backend/src/main/java/com/enerlytics/EnerlyticsBackendApplication.java` (`@EnableCaching`)
- `backend/src/main/resources/application.yml`
- `.env.example`
- `docs/EXTERNAL_INTEGRATIONS.md`
- `docs/PROJECT_STATE.md`

### Phase 20

- `backend/src/main/java/com/enerlytics/telemetry/api/event/MeterReadingValidatedEvent.java` (added `buildingId`)
- `backend/src/main/java/com/enerlytics/telemetry/application/TelemetryIngestionService.java` (publishes `MeterReadingValidatedEvent` to application listener channel)
- `backend/src/main/java/com/enerlytics/security/jwt/JwtAuthenticationFilter.java` (accepts `access_token` query parameter for SSE)
- `backend/src/main/java/com/enerlytics/live/energy/api/LiveEnergyController.java`
- `backend/src/main/java/com/enerlytics/live/energy/api/LiveEnergySnapshot.java`
- `backend/src/main/java/com/enerlytics/live/energy/api/MeterLiveReading.java`
- `backend/src/main/java/com/enerlytics/live/energy/api/TrendPoint.java`
- `backend/src/main/java/com/enerlytics/live/energy/application/LiveEnergyProperties.java`
- `backend/src/main/java/com/enerlytics/live/energy/application/LiveEnergyService.java`
- `backend/src/test/java/com/enerlytics/live/energy/application/LiveEnergyServiceTest.java`
- `backend/src/test/java/com/enerlytics/analytics/EnergyAggregationConsumerTest.java` (`buildingId` field)
- `backend/src/test/java/com/enerlytics/telemetry/application/TelemetryIngestionServiceTest.java` (`ApplicationEventPublisher` mock)
- `backend/src/main/resources/application.yml` (live energy config defaults)
- `.env.example` (live energy environment variables)
- `frontend/src/app/core/api/contracts.ts` (`LiveEnergySnapshot`, `MeterLiveReading`, `TrendPoint`)
- `frontend/src/app/features/live-energy/live-energy.component.ts`
- `frontend/src/app/features/live-energy/live-chart-options.ts`
- `frontend/src/app/features/live-energy/live-energy.service.ts`
- `frontend/src/app/features/live-energy/live-energy.service.spec.ts`
- `frontend/src/app/app.routes.ts` (live route now loads real component)
- `frontend/src/app/app.config.ts` (`LIVE_ENERGY_CONFIG` token provider)
- `frontend/angular.json` (component-style budget increase)
- `docs/PROJECT_STATE.md`

### Phase 19

- `frontend/src/app/features/overview/dashboard.models.ts`
- `frontend/src/app/features/overview/dashboard-data.service.ts`
- `frontend/src/app/features/overview/dashboard-filter-bar.component.ts`
- `frontend/src/app/features/overview/kpi-tile.component.ts`
- `frontend/src/app/features/overview/chart-card.component.ts`
- `frontend/src/app/features/overview/chart-options.ts`
- `frontend/src/app/features/overview/overview.component.ts`
- `frontend/src/app/features/overview/dashboard-data.service.spec.ts`
- `frontend/src/app/features/overview/chart-options.spec.ts`
- `frontend/src/app/core/charts/echart.component.ts`
- `frontend/src/app/core/charts/chart-theme.ts`
- `frontend/src/app/core/api/api-client.ts` (empty-path `orgPath` fix)
- `frontend/src/app/core/context/context.service.ts` (CUSTOM period, from/to params)
- `frontend/src/app/core/api/contracts.ts` (sites page, carbon, cost, alert DTOs)
- `frontend/package.json` (echarts 6.1.0)
- `docs/PROJECT_STATE.md`

### Phase 18

- `frontend/src/styles/_tokens.scss`
- `frontend/src/styles.scss`
- `frontend/src/index.html`
- `frontend/src/app/core/api/**` (api-error, api-client, api-error.interceptor, contracts)
- `frontend/src/app/core/auth/**` (token-storage, auth.service, auth.interceptor, auth.guard, has-authority.directive)
- `frontend/src/app/core/context/context.service.ts`
- `frontend/src/app/core/ui/**` (loading/empty/error state components)
- `frontend/src/app/core/layout/**` (shell, nav-rail, breadcrumb, profile-menu)
- `frontend/src/app/features/login/login.component.ts`
- `frontend/src/app/features/overview/overview.component.ts`
- `frontend/src/app/shared/page-placeholder.component.ts`
- `frontend/src/app/shared/status-pages.component.ts`
- `frontend/src/app/app.config.ts`, `app.routes.ts`, `app.ts`, `app.spec.ts`
- `frontend/src/app/**/*.spec.ts` (6 spec files, 25 tests)
- `frontend/angular.json` (fileReplacements, bundle budget)
- `frontend/tsconfig.json` (strict mode)
- `frontend/eslint.config.js`
- `frontend/scripts/set-env.js` (writes both environment files)
- `frontend/package.json` (lint script, eslint dev dependencies)
- `docs/PROJECT_STATE.md`

### Phase 17

- `docs/UX_SPEC.md`
- `docs/PROJECT_STATE.md`

### Phase 16

- `backend/src/main/resources/db/migration/V1_11_0__forecast_schema.sql`
- `backend/src/main/java/com/enerlytics/forecast/domain/**`
- `backend/src/main/java/com/enerlytics/forecast/config/**`
- `backend/src/main/java/com/enerlytics/forecast/provider/**`
- `backend/src/main/java/com/enerlytics/forecast/infrastructure/persistence/**`
- `backend/src/main/java/com/enerlytics/forecast/application/EnergyForecastService.java`
- `backend/src/main/java/com/enerlytics/forecast/api/**`
- `backend/src/test/java/com/enerlytics/forecast/provider/ForecastProvidersTest.java`
- `backend/src/test/java/com/enerlytics/forecast/application/EnergyForecastServiceTest.java`
- `backend/src/main/resources/application.yml`
- `.env.example`
- `docs/PROJECT_STATE.md`

### Phase 15

- `backend/src/main/resources/db/migration/V1_10_0__alert_schema.sql`
- `backend/src/main/java/com/enerlytics/alert/domain/**`
- `backend/src/main/java/com/enerlytics/alert/infrastructure/persistence/**`
- `backend/src/main/java/com/enerlytics/alert/application/AlertEvaluationService.java`
- `backend/src/main/java/com/enerlytics/alert/application/AlertLifecycleService.java`
- `backend/src/main/java/com/enerlytics/alert/application/AlertMetricsResolver.java`
- `backend/src/main/java/com/enerlytics/alert/application/AlertOutbox.java`
- `backend/src/main/java/com/enerlytics/alert/api/AlertController.java`
- `backend/src/main/java/com/enerlytics/alert/api/dto/**`
- `backend/src/main/java/com/enerlytics/alert/api/event/**`
- `backend/src/test/java/com/enerlytics/alert/application/AlertEvaluationServiceTest.java`
- `docs/PROJECT_STATE.md`

### Phase 14

- `backend/src/main/resources/db/migration/V1_9_0__anomaly_schema.sql`
- `backend/src/main/java/com/enerlytics/anomaly/domain/**`
- `backend/src/main/java/com/enerlytics/anomaly/detection/**`
- `backend/src/main/java/com/enerlytics/anomaly/config/**`
- `backend/src/main/java/com/enerlytics/anomaly/infrastructure/persistence/**`
- `backend/src/main/java/com/enerlytics/anomaly/application/**`
- `backend/src/main/java/com/enerlytics/anomaly/api/**`
- `backend/src/test/java/com/enerlytics/anomaly/detection/InterpretableDetectorsTest.java`
- `backend/src/test/java/com/enerlytics/anomaly/application/AnomalyDetectionServiceTest.java`
- `backend/src/main/resources/application.yml`
- `.env.example`
- `docs/PROJECT_STATE.md`

### Phase 13

- `backend/src/main/resources/db/migration/V1_8_0__billing_schema.sql`
- `backend/src/main/java/com/enerlytics/billing/domain/**`
- `backend/src/main/java/com/enerlytics/billing/infrastructure/persistence/**`
- `backend/src/main/java/com/enerlytics/billing/application/TariffService.java`
- `backend/src/main/java/com/enerlytics/billing/application/CostCalculationService.java`
- `backend/src/main/java/com/enerlytics/billing/application/CostAnalyticsService.java`
- `backend/src/main/java/com/enerlytics/billing/api/**`
- `backend/src/test/java/com/enerlytics/billing/application/CostCalculationServiceTest.java`
- `backend/src/test/java/com/enerlytics/billing/api/CostAnalyticsApiIntegrationTest.java`
- `docs/PROJECT_STATE.md`

### Phase 12D

- `backend/src/main/resources/db/migration/V1_7_0__carbon_emission_schema.sql`
- `backend/src/main/java/com/enerlytics/carbon/domain/CarbonEmissionEntity.java`
- `backend/src/main/java/com/enerlytics/carbon/infrastructure/persistence/CarbonEmissionRepository.java`
- `backend/src/main/java/com/enerlytics/carbon/infrastructure/persistence/CarbonIntensityObservationRepository.java`
- `backend/src/main/java/com/enerlytics/carbon/application/CarbonEmissionCalculationService.java`
- `backend/src/main/java/com/enerlytics/carbon/application/CarbonAnalyticsService.java`
- `backend/src/main/java/com/enerlytics/carbon/api/CarbonAnalyticsController.java`
- `backend/src/main/java/com/enerlytics/carbon/api/dto/**`
- `backend/src/main/java/com/enerlytics/meter/infrastructure/persistence/MeterRepository.java`
- `backend/src/test/java/com/enerlytics/carbon/application/CarbonEmissionCalculationServiceTest.java`
- `backend/src/test/java/com/enerlytics/carbon/api/CarbonAnalyticsApiIntegrationTest.java`
- `docs/PROJECT_STATE.md`

### Previous Phases

- `backend/src/main/resources/db/migration/V1_2_0__meter_schema.sql`
- `backend/src/main/java/com/enerlytics/meter/**`
- `contracts/openapi/meters.yaml`
- `backend/src/main/resources/db/migration/V1_1_0__facility_schema.sql`
- `backend/src/main/java/com/enerlytics/facility/**`
- `backend/src/main/java/com/enerlytics/organization/**`
- `backend/src/main/java/com/enerlytics/identity/**`
- `contracts/openapi/facilities.yaml`
- `contracts/openapi/identity.yaml`
- `docs/SECURITY.md`

### Existing Documentation

- `docs/PRODUCT_REQUIREMENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/DATA_MODEL.md`
- `docs/ADR/README.md`
- `docs/ADR/0001-modular-monolith.md`
- `docs/ADR/0002-deployment-topology-and-service-boundaries.md`
- `docs/ADR/0003-kafka-outbox-and-telemetry-guarantees.md`
- `docs/ADR/0004-sse-first-realtime-delivery.md`
- `docs/API_CONVENTIONS.md`
- `docs/TEST_STRATEGY.md`
- `docs/DEPLOYMENT.md`
- `docs/UX_SPEC.md`
- `docs/CHANGELOG.md`

## Technology Versions

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.5.16 |
| Maven | 3.9.16 |
| Angular | 22.1.7 |
| jjwt | 0.12.6 |
| springdoc-openapi | 2.8.5 |
| PostgreSQL | 18.6 |
| Apache Kafka | 4.3.1 |
| Redis | 8.10.1 |

## Tests and Validation Executed

### Backend

```text
mvnw clean test
mvnw clean package -DskipTests
```

Results:

- `EnerlyticsBackendApplicationTests`: 1/1 passed
- `IdentityIntegrationTest`: 9/9 passed
- `SiteRepositoryTest`: 5/5 passed
- `SiteServiceTest`: 3/3 passed
- `FacilityApiIntegrationTest`: 6/6 passed
- `MeterRepositoryTest`: 3/3 passed
- `MeterServiceTest`: 3/3 passed
- `MeterApiIntegrationTest`: 4/4 passed
- `TelemetryValidatorTest`: 7/7 passed
- `TelemetryIngestionServiceTest`: 3/3 passed
- `TelemetryIngestionKafkaIntegrationTest`: 15/15 passed
- `EnergyAggregationServiceTest`: 7/7 passed
- `EnergyAggregationConsumerTest`: 3/3 passed
- `EnergyAnalyticsApiIntegrationTest`: 4/4 passed
- `EnergyAggregationBenchmarkTest`: 1/1 passed
- `MockCarbonIntensityProviderTest`: 5/5 passed
- `ElectricityMapsCarbonIntensityProviderTest`: 9/9 passed
- `CarbonIntensityObservationRepositoryTest`: 2/2 passed
- `CarbonIntensityProviderSelectionTest`: 1/1 passed
- `CarbonEmissionCalculationServiceTest`: 12/12 passed
- `CarbonAnalyticsApiIntegrationTest`: 2/2 passed
- `CostCalculationServiceTest`: 14/14 passed
- `CostAnalyticsApiIntegrationTest`: 1/1 passed
- `InterpretableDetectorsTest`: 6/6 passed
- `AnomalyDetectionServiceTest`: 5/5 passed
- `AlertEvaluationServiceTest`: 4/4 passed
- `ForecastProvidersTest`: 6/6 passed
- `EnergyForecastServiceTest`: 5/5 passed
- `LiveEnergyServiceTest`: 5/5 passed
- **Total: 151 tests passed, 0 failures**
- Package build produced the Spring Boot executable JAR.

Energy benchmark dataset and observed development-host timings are documented in
`docs/ENERGY_AGGREGATION.md`. The benchmark covers 100 meters, 14 days, 134,400
raw readings, bounded reconciliation queries, and a 336-bucket analytics read.

### Telemetry Simulator

```text
mvn --file backend/telemetry-simulator/pom.xml clean test
mvn --file backend/telemetry-simulator/pom.xml clean package -DskipTests
```

Results:

- `LoadProfileCalculatorTest`: 5/5 passed
- `TelemetryGeneratorTest`: 4/4 passed
- `SimulatorEngineTest`: 3/3 passed
- `TelemetryEventProducerTest`: 1/1 passed
- **Total: 13 tests passed, 0 failures**
- Package build produced the simulator executable JAR.

### Frontend

```text
npm run lint
npx ng test
npm run build
```

Results:

- ESLint (`angular-eslint` + `typescript-eslint` flat config): clean, 0 errors
- Vitest: 9 spec files, 50 tests passed, 0 failures
  - `api-error.spec.ts` (4), `context.service.spec.ts` (6),
    `auth.service.spec.ts` (6), `auth.interceptor.spec.ts` (4),
    `has-authority.directive.spec.ts` (3), `app.spec.ts` (2),
    `dashboard-data.service.spec.ts` (11), `chart-options.spec.ts` (7),
    `live-energy.service.spec.ts` (7)
- Production build: `ng build` succeeded; ECharts stays in lazy chunks
  (~560 kB across `charts`/`components`/`renderers` loaded only with the
  dashboard routes); initial bundle 557 kB raw / 137 kB estimated transfer
- The host's system Node (24.6) is below Angular 22's minimum; builds used the
  portable Node 24.21.0 toolchain under `.runtime/`

### Secret Scan

- No real credentials are committed.
- `.env.example` contains only placeholder configuration keys.

## Unresolved Issues

### Live Energy Monitoring

- Live state is held only in memory; a server restart loses the live view until
  the next telemetry arrives. The authoritative telemetry store remains the
  database, and the current snapshot API regenerates from in-memory state, so
  there is no data loss, but warm-start behavior may be enhanced later by
  replaying the most recent reading per meter.
- The snapshot endpoint is intentionally lightweight and does not query the
  database; if no SSE subscribers or readings have recently arrived it returns
  an empty-scope snapshot.
- Per-meter display names are not pre-fetched; the SSE payload includes a meter
  name only when the service already knows it. A future enhancement can hydrate
  names from the meter registry on first sight of a meter.
- Building/meter filter dropdowns are populated from the snapshot or a separate
  API call; there is no persisted “favorite meters” list yet.
- Meter offline detection uses a single global threshold; per-meter or per-site
  thresholds are future work.

### Telemetry Simulator

- The simulator can now read from a registry API, but it still defaults to JDBC
  for local convenience. In production, registry mode with `SIMULATOR_USE_REGISTRY=true`
  and a strong `SIMULATOR_REGISTRY_API_KEY` should be used.
- No automated `OFFLINE` transition based on missed simulated heartbeats.
- Physical device credential provisioning is not implemented.
- Multi-channel meters are not yet modeled.
- Simulation profiles are fixed constants; user-defined curves and holiday
  calendars are future work.

### Telemetry Ingestion

- The outbox relay marks records published synchronously; retry metadata such as
  `attempt_count` and `last_error_at` is not yet captured.
- Energy/power consistency checks against the meter's configured interval and
  physical bounds are basic; site-specific thresholds and calibrated ranges are
  future work.
- A dedicated DLQ topic is not yet wired; invalid samples are persisted to
  `meter_reading_rejected` and emitted as `MeterReadingRejected` events instead.

### Carbon Intensity Provider

- The implementation supports the Electricity Maps v3 endpoints (`/carbon-intensity/latest`, `/history`, `/forecast`).
- Only Electricity Maps and the deterministic mock provider are wired; a priority-ordered fallback chain is future work.
- Cache TTL is global; per-zone TTL overrides are future work.
- Resilience4j metrics are auto-registered by the starter; explicit dashboards and alerts are future work.
- Forecast availability depends on the Electricity Maps subscription plan.

### Energy Aggregation

- Day and month buckets currently use UTC boundaries; site-local reporting periods are a future enhancement.
- Reconciliation resolves the meter's current facility hierarchy because effective-dated meter-location history is not yet modeled.
- Recompute-based incremental processing prioritizes correctness and replay safety but can rebuild up to 20 bounded buckets for one reading.
- `EnergyAggregationUpdated` outbox events are not emitted yet; they will be added with downstream carbon processing.
- The documented benchmark uses H2 in PostgreSQL compatibility mode. Production PostgreSQL query plans and p95/p99 targets remain a production-hardening task.

### Carbon Emissions

- Emission buckets are computed on demand by the analytics APIs rather than
  incrementally by a Kafka consumer; a scheduled/event-driven emission pipeline
  should be added once `EnergyAggregationUpdated` events exist.
- Factor resolution is zone-level via `site.grid_region_code` (falling back to
  `site.country`); zones/buildings inherit their site's region. Meters in
  archived sites are excluded because only ACTIVE meters are resolved.
- Staleness is a fixed 24-hour window relative to each energy bucket start; a
  configurable per-provider freshness policy is future work.
- Carbon recalculation replaces the bucket row in place (new `calculation_run_id`);
  a full revision history table for audit replay is future work.
- `QUARTER_HOUR` granularity is intentionally unsupported for carbon; emissions
  are computed from hourly factors and aggregate to HOUR/DAY/MONTH.

### Billing / Tariff Engine

- Tariffs are assigned per site; organization-level cost buckets require a
  single currency across contributing meters — mixed-currency buckets are
  marked `UNAVAILABLE` with null costs rather than producing a meaningless sum.
- Day-type classification uses the local calendar date of each hour (an
  overnight rate crossing into Saturday is priced as weekend); holiday
  calendars are future work.
- Rate windows are evaluated at hourly resolution; sub-hour boundary splits
  within a single hour bucket use that hour's start instant.
- Demand charge is `max(hourly peakPowerKw × demandRatePerKw)` per bucket,
  applied only from windows carrying a demand rate; true billing-period demand
  ratchets are future work.
- Cost buckets are computed on demand like carbon emissions; a scheduled or
  event-driven materialization job is future work.
- Currency conversion is not supported; baseline comparison rejects periods in
  different currencies.

### Energy Forecasting

- Seasonal positions, day boundaries, and trend windows are UTC-based (matching
  aggregate buckets). Site-local seasonal baselines and holiday calendars are
  future work.
- Prediction intervals use `z×sigma` of same-position samples — a residual
  proxy, not a calibrated coverage guarantee.
- `NEXT_7_DAYS` forecasts daily totals; intraday shape (hour-of-day curves) is
  only modeled by the 24-hour horizon.
- Evaluation is manual/scheduled via the API (`POST /runs/{id}/evaluate`);
  automatic evaluation after aggregation reconciles is future work.
- `EXTERNAL_MODEL` is reserved in schema and enum for a future ML provider
  implementing `ForecastProvider`; no remote forecasting service exists yet.
- Forecasts are computed on demand; scheduled rolling regeneration and
  forecast-vs-actual drift monitoring are future work.

### Alert Engine

- Alert rules are persisted per organization and scoped to `ORGANIZATION`,
  `SITE`, `BUILDING`, or `METER`. Parent-facility inheritance is not applied;
  a site-scoped rule does not automatically cover the site's meters or buildings.
- Cooldown deduplication uses a simple `lastTriggeredAt` per rule. Cross-entity
  deduplication and multi-condition composite rules are future work.
- Lifecycle transitions validate actor identity but do not record a transition
  history table; only the most recent acknowledge/resolve metadata is stored.
- Metrics for `CARBON_INTENSITY_HIGH` and `TARGET_EXCEEDED` require the carbon
  and target materialized data paths; target evaluation currently resolves
  available aggregates and anomaly counts.
- Outbox events are serialized and enqueued synchronously in the alert
  transaction; a background relay publishes them to Kafka like telemetry
  outbox events. Retry metadata per outbox record is future work.
- Alert notifications (email, SMS, webhooks) are not implemented; the events
  provide the integration point.

### Anomaly Detection

- Detection currently operates on canonical UTC hourly aggregates. Site-local
  same-hour baselines and holiday/calendar segmentation are future work.
- Thresholds, window lengths, startup duration, minimum completeness, and
  baseline sample requirements are environment-configurable globally; per-site
  and per-meter policy overrides are future work.
- Maintenance windows support organization-wide or exact entity scopes. Parent
  facility maintenance inheritance (for example, site window suppressing its
  meters) requires effective hierarchy traversal and is future work.
- Detection runs on demand through the API. Incremental execution from
  `EnergyAggregationUpdated` and notification routing are future work.
- Confidence is a deterministic score derived from threshold exceedance, not a
  calibrated probability. The detector interface is the extension point for
  later versioned statistical or ML models.

### Meter Domain

- No meter channel abstraction yet; all readings are associated with a single meter
  entity until multi-channel meters are modeled.
- Metadata is stored as a JSON string; typed JSONB with a schema registry should
  be introduced when telemetry attributes become more complex.

### Facility Domain

- No address normalization, geocoding, or country-specific validation.
- Effective dating and versioning of sites/buildings/zones are not implemented.
- Soft-delete archive behavior does not cascade to children.

### Pagination and Sorting

- `PageableFactory` only accepts pairs of `property,direction` and silently
  ignores an odd trailing element. A more robust parser should be added if
  clients need multi-column sorting with arbitrary defaults.
- Maximum page size is hardcoded at 100.

### Organization Management

- `POST /api/v1/organizations` is restricted to `PLATFORM_ADMIN`. A product
  decision is needed on whether organization admins can create sub-organizations.
- Listing organizations for the current user is only available through
  `GET /auth/me`; a dedicated paginated `/organizations` list may be needed.

### Frontend Foundation

- Tokens are stored in `localStorage`; migrating to httpOnly cookie storage or
  a BFF pattern is a production-hardening decision.
- `ContextService.initFromRoute` reads the root ActivatedRoute snapshot once at
  shell init; deep-linking into nested param-driven routes is future work.
- `contracts.ts` is hand-maintained from `contracts/openapi/*.yaml`; generating
  types from the published OpenAPI schema is future work.
- The host's system Node (24.6) does not satisfy Angular 22's engine
  requirement (≥24.15); the portable `.runtime/node` toolchain is required
  for lint/test/build on this machine.

### Executive Dashboard

- No backend endpoint exists for renewable-source percentage or
  sustainability targets, so the Renewable tile renders `N/A` and the
  target card renders an explicit "not yet modeled" empty state — no
  fabricated values. Both are wired to light up once the APIs exist.
- "Energy by Site" batches one bounded (≤12 sites) DAY-bucketed energy query
  per active site because there is no org-level per-site breakdown endpoint;
  a dedicated backend endpoint would remove the fan-out.
- KPI deltas under "previous period" comparison use the same elapsed window
  yesterday for the Today tiles and an equal-length prior range for charts;
  a same-period-last-year mode is available in `ContextService` but not yet
  offered in the filter bar.
- Browser-level visual verification used a contract-shaped mock API
  (`.runtime/mock-api.js`, gitignored) because this host has no Docker,
  PostgreSQL, or Kafka for the real backend; end-to-end verification against
  a live backend remains a release-gate task.
- Building-level filtering is modeled in `ContextService` but not yet
  exposed in the dashboard filter bar (no building list endpoint consumed).
- Click-to-drill from chart segments and brush-zoom-to-range promotion from
  UX_SPEC §4.2 are specified but not yet implemented; tiles currently
  link through to their domain surfaces.

### Local Tooling

- The current host does not have Java 21, Maven, or Docker on PATH. Validation
  used temporary portable Java 21, Maven 3.9.16, and Node 24.21.0 toolchains.

## Technical Debt

- `SecurityConfig` still emits a Spring Security deprecation warning. The
  deprecated usage should be identified and removed.
- The test utility for creating users/organizations/tokens is duplicated across
  integration tests; extract a shared test helper before adding the next domain
  module.
- `IllegalArgumentException` is mapped globally to HTTP 400, which may catch
  unintended runtime cases. Consider domain-specific exceptions for business
  rule failures.
- `GlobalExceptionHandler` uses a generated UUID per error; framework-level
  correlation propagation should replace it once tracing is configured.
- The simulator module is built independently; consider a root aggregator POM
  or CI matrix so both backend and simulator are validated together.
- The internal API key is a shared secret; evaluate mTLS or short-lived tokens
  for production service-to-service authentication.

## Next Recommended Task

1. Build the remaining §4.2 dashboard interactions: click-to-drill from chart
   segments into domain pages with filters preserved, and brush-zoom →
   "Apply as range" promotion on the consumption chart.
2. Implement the next business surface on the dashboard's proven patterns —
   the Live Energy monitoring page (SSE) or the Alerts center.
3. Backend: add renewable-source and sustainability-target endpoints so the
   two dashboard panels can render real values.
4. Add notification delivery channels (email, SMS, webhook, in-app) consuming alert outbox events.
5. Wire incremental anomaly/emission/cost recalculation to `EnergyAggregationUpdated` events or scheduled jobs.
6. Add CI pipelines that build and test `backend`, `backend/telemetry-simulator`, and `frontend`.
