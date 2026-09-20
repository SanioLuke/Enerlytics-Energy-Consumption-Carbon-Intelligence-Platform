# Enerlytics Project State

## Current Phase

Phase 7 — Identity and access-control foundation complete. The backend now has
JWT-based authentication, refresh-token rotation, role-based authorization,
tenant-aware access control, Flyway-managed identity/organization schema, and
integration tests covering the core security scenarios. No business domain
features beyond identity and a single organization read endpoint have been
implemented.

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
- Wired environment-driven configuration and committed the technical foundation.

### Phase 7 — Identity and Access Control

- Added JWT dependencies (jjwt), SpringDoc OpenAPI, and H2 for tests.
- Enabled Flyway and configured production/test `application.yml` profiles.
- Created Flyway migrations for `org.organization` and `iam.app_user`, `iam.role`,
  `iam.permission`, `iam.role_permission`, `iam.user_organization`,
  `iam.role_assignment`, and `iam.refresh_token`.
- Seeded system roles and permissions with an idempotent Java migration.
- Implemented JPA entities for Organization, User, Role, Permission,
  UserOrganization, RoleAssignment, and RefreshToken.
- Implemented stateless JWT authentication with `Authorization: Bearer` access
  tokens and opaque refresh-token hashes.
- Implemented refresh-token rotation: old token revoked, new token issued on
  every refresh.
- Implemented logout as refresh-token revocation.
- Implemented BCrypt password hashing (strength 12).
- Implemented `INVITED`, `ACTIVE`, and `SUSPENDED` account states; suspended users
  cannot authenticate or use access tokens.
- Configurable token expiry through `JWT_ACCESS_EXPIRATION_MS` and
  `JWT_REFRESH_EXPIRATION_MS`.
- Implemented RBAC with six predefined roles:
  - `PLATFORM_ADMIN`
  - `ORGANIZATION_ADMIN`
  - `FACILITY_MANAGER`
  - `ENERGY_ANALYST`
  - `SUSTAINABILITY_MANAGER`
  - `VIEWER`
- Implemented tenant isolation via `X-Organization-Id` header and membership
  checks. Platform administrators can access any organization; other users can
  only access organizations where they have an active membership and at least
  one active role assignment.
- Added `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`,
  `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, and
  `GET /api/v1/organizations/{id}`.
- Added standardized `application/problem+json` error responses for 400, 401,
  403, 404, and 500 with stable machine-readable codes and correlation ids.
- Added development-only seed admin mechanism configurable through environment
  variables (`DEV_ADMIN_EMAIL`, `DEV_ADMIN_PASSWORD`, etc.).
- Added OpenAPI contract at `contracts/openapi/identity.yaml` and SpringDoc UI
  support.
- Updated `docs/SECURITY.md` with the full authentication, authorization,
  tenant-isolation, and token-rotation design.

## Changed Files

### Phase 7

- `backend/pom.xml` — added jjwt, springdoc-openapi, and H2 test dependencies.
- `backend/src/main/resources/application.yml` — enabled Flyway, added JWT and
  dev-admin configuration.
- `backend/src/test/resources/application-test.yml` — H2 test profile.
- `backend/src/main/resources/db/migration/V1_0_0__identity_and_organization_foundation.sql`
- `backend/src/main/java/db/migration/V1_0_1__SeedRolesAndPermissions.java`
- `backend/src/main/java/com/enerlytics/config/JpaConfig.java`
- `backend/src/main/java/com/enerlytics/config/OpenApiConfig.java`
- `backend/src/main/java/com/enerlytics/config/SecurityConfig.java`
- `backend/src/main/java/com/enerlytics/config/DevelopmentIdentitySeeder.java`
- `backend/src/main/java/com/enerlytics/common/domain/AuditedEntity.java`
- `backend/src/main/java/com/enerlytics/common/api/Problem.java`
- `backend/src/main/java/com/enerlytics/common/api/GlobalExceptionHandler.java`
- `backend/src/main/java/com/enerlytics/identity/**`
- `backend/src/main/java/com/enerlytics/organization/**`
- `backend/src/main/java/com/enerlytics/security/**`
- `backend/src/test/java/com/enerlytics/identity/IdentityIntegrationTest.java`
- `contracts/openapi/identity.yaml`
- `docs/SECURITY.md`
- `.env.example`

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
- `README.md`

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
  - `validLoginReturnsTokens`
  - `invalidCredentialsReturn401`
  - `disabledAccountCannotLogin`
  - `expiredAccessTokenIsRejected`
  - `refreshFlowRotatesTokenAndRevokesOldOne`
  - `logoutRevokesRefreshToken`
  - `userWithoutRequiredRoleIsDenied`
  - `crossTenantAccessIsDenied`
  - `platformAdminCanAccessAnyOrganization`
- Package build produced `backend/target/enerlytics-backend-0.0.1-SNAPSHOT.jar`.

### Frontend

No frontend changes were required for this phase. The previous Angular build
remains valid.

### Secret Scan

- No real credentials are committed.
- `.env.example` contains only placeholder configuration keys.
- JWT secrets and dev-admin credentials must be supplied through environment
  variables; the application refuses to start if a JWT secret is shorter than
  32 characters.

## Unresolved Issues

### Identity/OAuth2

- Only local password authentication is implemented. OIDC/OAuth2 integration will
  be added when a provider and claim-mapping rules are approved.
- Password reset, email verification, invitation flows, and MFA are deferred to
  a later phase.
- The user-info endpoint returns all memberships. A chosen-active-organization
  endpoint and organization-switching flow may be needed for users with many
  organizations.

### RBAC

- Site/building-scoped assignments are modeled but not enforced because no site or
  building endpoints exist yet.
- Custom per-organization roles are not yet supported.
- Permission checks currently use permission strings in
  `@PreAuthorize("hasAuthority('...')")`. A custom expression helper may be
  introduced once tenant-scoped checks become more common.

### Token Management

- Refresh-token family detection and reuse detection are not implemented.
- There is no scheduled cleanup of expired revoked refresh tokens.
- Access tokens are not explicitly blacklisted on logout; they remain valid until
  their short expiry.

### Local Tooling

- The current host does not have Java 21, Maven, or Docker on PATH. Validation
  used temporary portable Java 21, Maven 3.9.16, and Node 24.15.0 toolchains.

## Technical Debt

- `SecurityConfig` uses a deprecation-deprecated `ProviderManager` constructor.
  This should be replaced with the non-deprecated
  `ProviderManager(AuthenticationProvider...)` constructor in a follow-up.
- `GlobalExceptionHandler` uses a generated UUID per error; the framework's
  request-scoped correlation ID should be used once distributed tracing is
  configured.
- The `OrganizationController` tenant check duplicates the filter's membership
  check. A shared tenant-aware annotation or expression would reduce repetition.
- H2 is used for tests. CI should run integration tests against PostgreSQL before
  accepting business-domain changes.

## Next Recommended Task

1. Set up CI with PostgreSQL, Kafka, and Redis services so tests run against the
   real backing stores.
2. Implement the next bounded domain module (e.g., `organization` admin CRUD or
   `site` hierarchy) behind the new RBAC and tenant boundaries.
3. Add password reset/invitation flows or OIDC integration based on product
   priorities.
4. Introduce audit logging for security-relevant events (login, logout, role
   changes, cross-tenant denials).
5. Begin the telemetry ingestion and meter-reading domain once identity is
   proven in CI.
