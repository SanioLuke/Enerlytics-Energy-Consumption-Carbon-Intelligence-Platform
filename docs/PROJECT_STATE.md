# Enerlytics Project State

## Current Phase

Phase 9 — Production-grade electricity meter management complete. Meters can be
registered, updated, activated, deactivated, commissioned, decommissioned,
located within the facility hierarchy, listed, searched, and heartbeats can
record last-seen time. All operations are secured by tenant-aware
authorization. Repository, service, and API integration tests pass. No telemetry
ingestion, energy, carbon, tariff, or analytics features have been implemented.

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

- Added Flyway migration `V1_2_0__meter_schema.sql` for `telemetry.meter`.
- Implemented `MeterEntity`, `MeterStatus`, `MeterType`, and DTOs with validation.
- Implemented `MeterService` with:
  - register meter
  - update meter
  - activate / deactivate
  - commission / decommission
  - assign location
  - list/search with filters
  - heartbeat / last-seen tracking
- Implemented `MeterController` under `/api/v1/organizations/{orgId}/meters` with
  tenant authorization using `TenantGuard` and `meter:read` / `meter:write`
  permissions.
- Enforced parent ownership checks for site/building/zone.
- Added business validation for reading interval, duplicate meter codes, and
  decommissioned-meter state changes.
- Added `MeterRepository` with JPA Specifications for combined filtering, search,
  and pagination.
- Added tests:
  - `MeterRepositoryTest` — tenant-safe lookups, code uniqueness, status filtering.
  - `MeterServiceTest` — create, duplicate code, status activation.
  - `MeterApiIntegrationTest` — full lifecycle, search, location assignment,
    heartbeat, viewer authorization denial, invalid interval validation.
- Added OpenAPI contract `contracts/openapi/meters.yaml`.

## Changed Files

### Phase 9

- `backend/src/main/resources/db/migration/V1_2_0__meter_schema.sql`
- `backend/src/main/java/com/enerlytics/meter/**`
- `backend/src/test/java/com/enerlytics/meter/MeterRepositoryTest.java`
- `backend/src/test/java/com/enerlytics/meter/MeterServiceTest.java`
- `backend/src/test/java/com/enerlytics/meter/MeterApiIntegrationTest.java`
- `contracts/openapi/meters.yaml`
- `docs/PROJECT_STATE.md`
- `README.md`

### Previous Phases

- `backend/src/main/resources/db/migration/V1_1_0__facility_schema.sql`
- `backend/src/main/java/com/enerlytics/facility/**`
- `backend/src/main/java/com/enerlytics/organization/**`
- `backend/src/main/java/com/enerlytics/common/api/PageResponse.java`
- `backend/src/main/java/com/enerlytics/common/api/PageableFactory.java`
- `backend/src/main/java/com/enerlytics/security/tenant/TenantGuard.java`
- `contracts/openapi/facilities.yaml`
- `backend/src/main/java/com/enerlytics/identity/**`
- `contracts/openapi/identity.yaml`
- `docs/SECURITY.md`

### Existing Documentation

- `docs/PRODUCT_REQUIREMENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/DATA_MODEL.md`
- `docs/EVENT_ARCHITECTURE.md`
- `docs/CALCULATION_SPEC.md`
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
| Maven | 3.9.16 (via wrapper) |
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
- **Total: 34 tests passed, 0 failures**
- Package build produced the Spring Boot executable JAR.

### Frontend

No frontend changes were required for this phase. The previous Angular build
remains valid.

### Secret Scan

- No real credentials are committed.
- `.env.example` contains only placeholder configuration keys.

## Unresolved Issues

### Meter Domain

- No meter channel abstraction yet; all readings will be associated with a single
  meter entity until multi-channel meters are modeled.
- No physical device provisioning flow or per-device credentials.
- Metadata is stored as a JSON string; typed JSONB with a schema registry should
  be introduced when telemetry attributes become more complex.
- No automated transition from `OFFLINE` to `ACTIVE` based on heartbeat age.

### Facility Domain

- No address normalization, geocoding, or country-specific validation.
- Effective dating and versioning of sites/buildings/zones are not implemented.
- Parent hierarchy validation does not enforce building belongs to site across
  the entire update surface (service-level checks exist; no DB exclusion
  constraint for future moves).
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

## Next Recommended Task

1. Set up CI to run the full backend test suite against PostgreSQL.
2. Implement telemetry ingestion endpoints and Kafka outbox/event publishing for
   `MeterReadingReceived` events, keeping tenant and idempotency guarantees.
3. Extract a shared test fixture helper for users, organizations, roles, and
   tokens.
4. Add audit logging for meter lifecycle mutations (create, update, commission,
   decommission) before production use.
5. Introduce meter channels and physical-device credential provisioning.
