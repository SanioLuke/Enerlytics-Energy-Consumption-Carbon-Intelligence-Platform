# Enerlytics Project State

## Current Phase

Phase 12A — Kafka end-to-end integration testing completed. The ingestion
pipeline is now exercised against a real embedded Kafka broker rather than only
unit mocks. Fifteen integration tests cover valid ingestion, malformed JSON,
schema/validation rejections, idempotency, database-failure redelivery,
consumer-offset behavior, and outbox publish retry.

Because `spring-kafka-test` does not yet support the Kafka 4.3.x broker wire
version used by the local Docker image, the Maven `kafka.version` override was
removed so Spring Boot manages a compatible Kafka client/test-broker line
(currently `3.9.2`). The production Docker Compose image remains
`apache/kafka:4.3.1`; the client library is wire-compatible.

A `KafkaConfig` bean now provides an explicit `KafkaTemplate<String, String>`
because Spring Boot's auto-configured `KafkaTemplate<?, ?>` could not satisfy
the `KafkaTemplate<String, String>` injection required by `OutboxRelay`.

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
- **Total: 59 tests passed, 0 failures**
- Package build produced the Spring Boot executable JAR.

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
  used temporary portable Java 21, Maven 3.9.16, and Node 24.15.0 toolchains.

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

1. Implement the telemetry ingestion consumer that reads `MeterReadingReceived`,
   validates samples, writes readings/rejections, and emits validated events via
   the transactional outbox.
2. Implement hourly energy aggregation and carbon calculation workers.
3. Extract a shared test fixture helper for users, organizations, roles, and
   tokens.
4. Add CI pipelines that build and test both `backend` and
   `backend/telemetry-simulator`.
5. Add Docker Compose service and README instructions for running the simulator
   locally against the development database and Kafka.
