# Enerlytics Project State

## Current Phase

Phase 8 — Organization and facility domain complete. CRUD and lifecycle
management are implemented for `Organization`, `Site`, `Building`, and `Zone`
with DTOs, validation, pagination, sorting, search, tenant authorization,
audit fields, and optimistic locking. Repository, service, and API integration
tests are in place and passing. No meter, telemetry, energy, carbon, tariff,
forecast, alert, or analytics features have been implemented.

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

- Added Flyway migration `V1_1_0__facility_schema.sql` for `org.site`,
  `org.building`, and `org.zone` tables with indexes and constraints.
- Implemented JPA entities for `Site`, `Building`, and `Zone` extending the
  audited base entity with optimistic locking.
- Implemented DTOs, validation, and service-layer mapping for all four hierarchy
  levels.
- Implemented CRUD and soft-delete (archive) lifecycle management:
  - `OrganizationService`
  - `SiteService`
  - `BuildingService`
  - `ZoneService`
- Implemented REST controllers with nested tenant-aware URLs:
  - `/api/v1/organizations`
  - `/api/v1/organizations/{orgId}/sites`
  - `/api/v1/organizations/{orgId}/sites/{siteId}/buildings`
  - `/api/v1/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}/zones`
- Added pagination, sorting, and search with allowlisted sort fields.
- Enforced tenant boundaries using the existing `X-Organization-Id` header and
  `TenantGuard` path checks.
- Added business validation: IANA timezone validation, latitude/longitude and
  floor-area constraints, closed-on-after-opened date checks, and parent/child
  ownership checks.
- Updated `GlobalExceptionHandler` to map `IllegalArgumentException` to HTTP 400.
- Added OpenAPI specification `contracts/openapi/facilities.yaml`.
- Added tests:
  - `SiteRepositoryTest` — repository pagination, search, tenant-safe lookups,
    audit fields.
  - `SiteServiceTest` — duplicate-code and invalid-timezone validation.
  - `FacilityApiIntegrationTest` — full CRUD lifecycle, pagination, sorting,
    search, building/zone hierarchy, tenant isolation, unauthorized role, and
    validation error scenarios.

## Changed Files

### Phase 8

- `backend/src/main/resources/db/migration/V1_1_0__facility_schema.sql`
- `backend/src/main/java/com/enerlytics/facility/**`
- `backend/src/main/java/com/enerlytics/organization/api/OrganizationController.java`
- `backend/src/main/java/com/enerlytics/organization/api/dto/CreateOrganizationRequest.java`
- `backend/src/main/java/com/enerlytics/organization/api/dto/UpdateOrganizationRequest.java`
- `backend/src/main/java/com/enerlytics/organization/api/dto/OrganizationResponse.java`
- `backend/src/main/java/com/enerlytics/organization/application/OrganizationService.java`
- `backend/src/main/java/com/enerlytics/organization/domain/OrganizationEntity.java`
- `backend/src/main/java/com/enerlytics/common/api/PageResponse.java`
- `backend/src/main/java/com/enerlytics/common/api/PageableFactory.java`
- `backend/src/main/java/com/enerlytics/security/tenant/TenantGuard.java`
- `backend/src/main/java/com/enerlytics/common/api/GlobalExceptionHandler.java`
- `backend/src/test/java/com/enerlytics/facility/SiteRepositoryTest.java`
- `backend/src/test/java/com/enerlytics/facility/SiteServiceTest.java`
- `backend/src/test/java/com/enerlytics/facility/FacilityApiIntegrationTest.java`
- `contracts/openapi/facilities.yaml`
- `docs/PROJECT_STATE.md`
- `README.md`

### Existing Documentation

- `docs/PRODUCT_REQUIREMENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/DATA_MODEL.md`
- `docs/EVENT_ARCHITECTURE.md`
- `docs/CALCULATION_SPEC.md`
- `docs/SECURITY.md`
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
- Total: 24 tests passed, 0 failures
- Package build produced the Spring Boot executable JAR.

### Frontend

No frontend changes were required for this phase. The previous Angular build
remains valid.

### Secret Scan

- No real credentials are committed.
- `.env.example` contains only placeholder configuration keys.

## Unresolved Issues

### Facility Domain

- No address normalization, geocoding, or country-specific validation yet.
- Site/building/zone effective-dated changes and versioning are not implemented.
- Parent hierarchy validation does not enforce building belongs to site across
  the entire update surface (service-level checks exist; no DB exclusion
  constraint for future moves).
- Soft-delete archive behavior does not cascade to children; deleting a site with
  active buildings/zones is currently allowed.

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
- The test utility for creating users/organizations/tokens is duplicated between
  `IdentityIntegrationTest` and `FacilityApiIntegrationTest`; extract a shared
  test helper when the next domain module is added.
- `IllegalArgumentException` is mapped globally to HTTP 400, which may catch
  unintended runtime cases. Consider domain-specific exceptions for business
  rule failures.
- `GlobalExceptionHandler` uses a generated UUID per error; framework-level
  correlation propagation should replace it once tracing is configured.

## Next Recommended Task

1. Set up CI to run the full backend test suite against PostgreSQL.
2. Implement the meter/channel domain and telemetry ingestion endpoints, keeping
   the same tenant and audit patterns.
3. Introduce a shared test fixture helper for users, organizations, roles,
   and tokens.
4. Add audit logging for facility lifecycle mutations (create, update, archive)
   before production use.
5. Decide on effective-dated site/building/zone versioning and implement it if
   required by the product.
