# Enerlytics Project State

## Current Phase

Phase 6 — Repository bootstrap complete. The backend, frontend, and local
infrastructure foundations are created, configured for environment-driven
configuration, and validated. No business features, domain entities, database
migrations, API contracts, or event schemas have been implemented.

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
- Defined a 34-significant-digit intermediate decimal policy, persisted scales,
  `HALF_EVEN` rounding, no-early-rounding rules, line-item/currency handling, and
  provenance requirements.
- Specified all 16 required deterministic calculations with inputs, output, unit,
  precision, rounding, failure behavior, and worked examples.

### Phase 6 — Repository Bootstrap

- Created the required repository structure:
  - `backend/` — Java 21 + Maven + Spring Boot 3.5.16 single-module backend
  - `frontend/` — Angular 22.1.x + strict TypeScript + SCSS + Angular Material
  - `infrastructure/` — Dockerfiles, Docker Compose, and Nginx configuration
  - `scripts/` — Local development helper scripts
- Added root metadata:
  - `.editorconfig`
  - `.gitignore`
  - `README.md`
  - `.env.example`
- Configured local development infrastructure:
  - PostgreSQL 18.6
  - Apache Kafka 4.3.1 (KRaft mode, no ZooKeeper)
  - Redis 8.10.1
  - Health checks for every service
- Wired environment-driven configuration for backend (`application.yml`) and
  frontend (`scripts/set-env.js` + `src/environments/`).
- Added Maven Wrapper so the backend can be built without a global Maven
  installation.
- Initialized the Git repository and committed the bootstrap files.

## Changed Files

### Phase 6

- `backend/pom.xml`
- `backend/.mvn/wrapper/maven-wrapper.properties`
- `backend/mvnw`
- `backend/mvnw.cmd`
- `backend/src/main/java/com/enerlytics/EnerlyticsBackendApplication.java`
- `backend/src/main/java/com/enerlytics/config/SecurityConfig.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/enerlytics/EnerlyticsBackendApplicationTests.java`
- `frontend/package.json`
- `frontend/angular.json`
- `frontend/tsconfig.json`
- `frontend/tsconfig.app.json`
- `frontend/tsconfig.spec.json`
- `frontend/scripts/set-env.js`
- `frontend/src/main.ts`
- `frontend/src/index.html`
- `frontend/src/styles.scss`
- `frontend/src/app/app.ts`
- `frontend/src/app/app.html`
- `frontend/src/app/app.scss`
- `frontend/src/app/app.config.ts`
- `frontend/src/app/app.routes.ts`
- `frontend/src/app/app.spec.ts`
- `frontend/src/environments/environment.ts`
- `frontend/src/environments/environment.production.ts`
- `.editorconfig`
- `.gitignore`
- `README.md`
- `.env.example`
- `infrastructure/compose/docker-compose.yml`
- `infrastructure/docker/backend/Dockerfile`
- `infrastructure/docker/frontend/Dockerfile`
- `infrastructure/docker/frontend/nginx.conf`
- `scripts/start-local.ps1`
- `scripts/start-local.sh`
- `scripts/stop-local.ps1`
- `scripts/stop-local.sh`

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
- `docs/SECURITY.md`
- `docs/TEST_STRATEGY.md`
- `docs/DEPLOYMENT.md`
- `docs/CHANGELOG.md`

## Bootstrap Decisions

- The backend remains a single Maven module. Physical separation of the
  telemetry worker will be handled later by runtime profiles or packaging, not by
  premature multi-module extraction.
- Spring Boot 3.5.16 is the chosen 3.x baseline because the architecture
  explicitly requested a stable Spring Boot 3.x release.
- Angular 22.1.7 is the chosen stable frontend baseline; Angular 22.2 remains
  pre-release.
- No business domain packages, entities, repositories, REST controllers, Kafka
  producers/consumers, calculations, or Angular business components were added.
- Flyway is on the classpath but disabled by default (`FLYWAY_ENABLED=false`) so
  the backend compiles and starts without a database in this bootstrap phase.
- Spring Security is included with a permit-all placeholder filter chain to keep
  actuator health checks reachable during local development.
- Angular configuration is build-time environment-driven: `scripts/set-env.js`
  reads `.env` and writes `src/environments/environment.ts` before `ng build`.
- Local infrastructure images are pinned to known versions (`postgres:18.6`,
  `apache/kafka:4.3.1`, `redis:8.10.1`) rather than floating `latest` tags.
- Kafka runs in KRaft mode for a single-node local deployment, avoiding
  ZooKeeper.
- Every Docker Compose service defines a `healthcheck`.
- No real secrets are committed; only `.env.example` contains placeholder values.

## Database Changes

None implemented. PostgreSQL, Kafka, and Redis services are declared in Docker
Compose with named volumes, but no schema or Flyway migration was created.

## APIs, Events, and Business Code Created

None. The backend exposes only the Spring Boot Actuator health endpoints. The
frontend contains only an application shell with a Material toolbar and a
placeholder card.

## Technology Versions

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.5.16 |
| Maven | 3.9.16 (via wrapper) |
| Angular | 22.1.7 |
| TypeScript | ~6.0.2 |
| PostgreSQL | 18.6 |
| Apache Kafka | 4.3.1 |
| Redis | 8.10.1 |

## Tests and Validation Executed

### Backend

```text
mvnw clean test package
```

Result: `BUILD SUCCESS` with Java 21, Spring Boot 3.5.16, and Maven 3.9.16.
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0.

### Frontend

```text
npm ci
npm run build
```

Result: Build succeeded. Generated `frontend/dist/frontend` with main bundle
(217 kB raw) and styles bundle (53 kB raw).

### Docker Compose

- YAML syntax of `infrastructure/compose/docker-compose.yml` was validated with
  Python PyYAML.
- Full `docker compose config` and container startup could not be executed
  because Docker is not installed on the current host.

### Secret Scan

- Only `.env.example` exists; no real `.env` file is present.
- No hardcoded passwords, tokens, API keys, or secrets were found outside the
  example template.

### Repository

- Git repository initialized.
- Initial commit created with 62 files.

## Unresolved Issues

### Local Tooling

- The current host does not have Java 21, Maven, or Docker on PATH. Validation
  used temporary portable Java 21, Maven 3.9.16, and Node 24.15.0 toolchains.
  Contributors who install Java 21 and Docker locally will be able to run all
  commands as documented in `README.md`.

### Docker Image Availability

- The selected image tags (`postgres:18.6`, `apache/kafka:4.3.1`,
  `redis:8.10.1`, `eclipse-temurin:21-jre-alpine`, `nginx:1.27-alpine`,
  `node:24.15.0-bookworm`, `maven:3.9.16-eclipse-temurin-21`) should be pulled
  when a Docker environment is available. Verify tags before first deployment.

### Frontend Runtime Configuration

- The current approach generates `environment.ts` at build time from `.env`.
  A runtime configuration loader (`/assets/config.json` + APP_INITIALIZER) should
  be evaluated before the first deployment so that a single image can serve
  multiple environments.

## Technical Debt

- `SecurityConfig` is a permit-all placeholder; real authentication and
  authorization will replace it when identity/RBAC features begin.
- `EnerlyticsBackendApplicationTests` is a minimal compile-time smoke test. Full
  Spring context tests will be added after database and message infrastructure
  are reachable in CI.
- Frontend unit tests rely on the default `app.spec.ts` generated by Angular CLI.
  Domain-specific tests will be added with business components.
- Flyway is disabled by default; it must be enabled once the first migration is
  authored.
- No CI pipeline, linting rules beyond Angular defaults, or pre-commit hooks
  have been configured yet.

## Next Recommended Task

1. Set up a CI pipeline (GitHub Actions or equivalent) with Java 21, Node 24.x,
   and Docker services so all builds, tests, and Docker Compose health checks
   run automatically.
2. Add the first Flyway migration for the identity/organization/site/meter
   core tables from `docs/DATA_MODEL.md`.
3. Implement the first bounded-domain module (likely `organization` or
   `identity`) with REST endpoints, DTOs, MapStruct mappers, and repository
   tests.
4. Introduce runtime configuration loading for the frontend so a single Docker
   image can target multiple backends.
5. Add Kafka topic provisioning and the first idempotent telemetry consumer
   once the meter-reading schema is in place.

Do not add business features until the repository bootstrap is running cleanly
in CI and the next module scope is explicitly approved.
