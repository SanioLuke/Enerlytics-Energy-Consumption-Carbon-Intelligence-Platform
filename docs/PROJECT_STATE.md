# Enerlytics Project State

## Current Phase

Phase 12D — Carbon emissions processing completed. Enerlytics now computes
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
- **Total: 105 tests passed, 0 failures**
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

No frontend changes were required for this phase. The previous Angular build
remains valid.

### Secret Scan

- No real credentials are committed.
- `.env.example` contains only placeholder configuration keys.

## Unresolved Issues

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

1. Add real-time analytics APIs and alert processing (threshold/anomaly rules over aggregates and emissions).
2. Wire incremental emission recalculation to `EnergyAggregationUpdated` events or a scheduled job.
3. Add real-time energy/carbon analytics delivery to the frontend.
4. Extract a shared test fixture helper for users, organizations, roles, and tokens.
5. Add CI pipelines that build and test both `backend` and `backend/telemetry-simulator`.
