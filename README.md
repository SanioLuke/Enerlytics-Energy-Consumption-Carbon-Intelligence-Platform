# Enerlytics

A production-grade Energy Consumption & Carbon Intelligence Platform.

This repository currently contains the technical foundation plus the **identity
and access-control** backend module. It supports JWT authentication, RBAC, and
organization-level tenant isolation. The only exposed business endpoints are
authentication and a read-only organization endpoint; energy-domain features
remain to be implemented.

## Repository structure

```text
backend/              Java 21 + Spring Boot 3.x backend (single-module monolith)
frontend/             Angular 22 + Angular Material + SCSS shell
infrastructure/       Dockerfiles, Docker Compose, and runtime configuration
  compose/            docker-compose.yml for local services
  docker/             Base Dockerfiles for backend and frontend
docs/                 Architecture, requirements, data model, and ADRs
scripts/              Local development helper scripts
```

## Prerequisites

- **Java 21** (Temurin or equivalent) — required to build the backend.
- **Node.js** `^22.22.3 || ^24.15.0 || ^26.0.0` — required for the Angular frontend.
- **npm** 10+ — used by the frontend workspace.
- **Docker Desktop** or Docker Engine + Compose v2 — required to run local infrastructure.
- (Optional) **Maven** 3.9+ — not required; the repository includes the Maven Wrapper.
- (Optional) **Angular CLI** 22.1.x — not required; `npx ng` or `npm run` works without a global install.

If you are on a machine that does not have Java 21, Maven, or Docker, you will
not be able to run the corresponding build or container steps locally until
those tools are installed. The backend and frontend files themselves are
standard and do not depend on temporary helper paths.

## Quick start

1. **Clone or open the repository.**
2. **Copy the environment template:**

   ```bash
   cp .env.example .env
   ```

3. **Start the local infrastructure** (PostgreSQL, Kafka, Redis):

   PowerShell:

   ```powershell
   .\scripts\start-local.ps1
   ```

   Bash:

   ```bash
   ./scripts/start-local.sh
   ```

   Or run directly from the compose directory:

   ```bash
   docker compose --env-file ../../.env up -d
   ```

4. **Inspect health:**

   ```bash
   docker compose ps
   docker compose logs -f
   ```

   Every service defines a `healthcheck`. Wait until the status is `healthy`.

5. **Build and test the backend:**

   ```bash
   cd backend
   .\mvnw.cmd clean test package        # Windows
   ./mvnw clean test package            # Linux/macOS
   ```

6. **Build and test the telemetry simulator:**

   ```bash
   cd backend/telemetry-simulator
   mvn clean test package                 # Requires Maven 3.9+
   ```

   The simulator is a separate Spring Boot application that produces
   `MeterReadingReceived` Kafka events for active simulated meters.

7. **Build and test the frontend:**

   ```bash
   cd frontend
   npm ci
   npm run build
   ```

7. **Stop the local infrastructure:**

   PowerShell:

   ```powershell
   .\scripts\stop-local.ps1
   ```

   Bash:

   ```bash
   ./scripts/stop-local.sh
   ```

## API endpoints

The backend exposes the following identity endpoints under `/api/v1`:

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | Authenticate with email/password; receive access/refresh tokens. |
| `POST` | `/auth/refresh` | Rotate refresh token into a new token pair. |
| `POST` | `/auth/logout` | Revoke the supplied refresh token. |
| `GET`  | `/auth/me` | Get the current user's profile and memberships. |
| `POST` | `/organizations` | Create an organization (`PLATFORM_ADMIN`). |
| `GET`  | `/organizations/{id}` | Read an organization (requires `organization:read`). |
| `PUT`  | `/organizations/{id}` | Update an organization (requires `organization:write`). |
| `DELETE` | `/organizations/{id}` | Archive an organization (requires `organization:write`). |
| `POST` | `/organizations/{orgId}/sites` | Create a site (requires `site:write`). |
| `GET`  | `/organizations/{orgId}/sites` | List/search sites (requires `site:read`). |
| `GET`  | `/organizations/{orgId}/sites/{siteId}` | Read a site (requires `site:read`). |
| `PUT`  | `/organizations/{orgId}/sites/{siteId}` | Update a site (requires `site:write`). |
| `DELETE` | `/organizations/{orgId}/sites/{siteId}` | Archive a site (requires `site:write`). |
| `POST` | `/organizations/{orgId}/sites/{siteId}/buildings` | Create a building (requires `site:write`). |
| `GET`  | `/organizations/{orgId}/sites/{siteId}/buildings` | List/search buildings (requires `site:read`). |
| `GET`  | `/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}` | Read a building (requires `site:read`). |
| `PUT`  | `/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}` | Update a building (requires `site:write`). |
| `DELETE` | `/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}` | Archive a building (requires `site:write`). |
| `POST` | `/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}/zones` | Create a zone (requires `site:write`). |
| `GET`  | `/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}/zones` | List/search zones (requires `site:read`). |
| `GET`  | `/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}/zones/{zoneId}` | Read a zone (requires `site:read`). |
| `PUT`  | `/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}/zones/{zoneId}` | Update a zone (requires `site:write`). |
| `DELETE` | `/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}/zones/{zoneId}` | Archive a zone (requires `site:write`). |
| `POST`   | `/organizations/{orgId}/meters` | Register a meter (requires `meter:write`). |
| `GET`    | `/organizations/{orgId}/meters` | List/search meters (requires `meter:read`). |
| `GET`    | `/organizations/{orgId}/meters/{meterId}` | Read a meter (requires `meter:read`). |
| `PUT`    | `/organizations/{orgId}/meters/{meterId}` | Update a meter (requires `meter:write`). |
| `POST`   | `/organizations/{orgId}/meters/{meterId}/activate` | Activate a meter (requires `meter:write`). |
| `POST`   | `/organizations/{orgId}/meters/{meterId}/deactivate` | Deactivate a meter (requires `meter:write`). |
| `POST`   | `/organizations/{orgId}/meters/{meterId}/commission` | Commission a meter (requires `meter:write`). |
| `POST`   | `/organizations/{orgId}/meters/{meterId}/decommission` | Decommission a meter (requires `meter:write`). |
| `POST`   | `/organizations/{orgId}/meters/{meterId}/location` | Assign meter location (requires `meter:write`). |
| `POST`   | `/organizations/{orgId}/meters/{meterId}/heartbeat` | Record a meter heartbeat (requires `meter:write`). |

All authenticated requests must include:

```text
Authorization: Bearer <access-token>
X-Organization-Id: <organization-uuid>
```

OpenAPI/Swagger UI is available at `http://localhost:8080/swagger-ui.html` once
the backend is running.

## Telemetry simulator

A separate Spring Boot module in `backend/telemetry-simulator` produces realistic
Kafka `MeterReadingReceived` events for meters flagged as simulated and active.

By default it reads the simulated-meter registry directly from PostgreSQL. Set
`SIMULATOR_USE_REGISTRY=true` to use the backend registry API instead, which
decouples the simulator from the database.

Run it after starting the local database and Kafka:

```bash
cd backend/telemetry-simulator
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DDATABASE_URL=jdbc:postgresql://localhost:5432/enerlytics -DDATABASE_USERNAME=enerlytics -DDATABASE_PASSWORD=enerlytics -DKAFKA_BOOTSTRAP_SERVERS=localhost:9092"
```

Useful environment variables:

| Variable | Default | Description |
|---|---|---|
| `SIMULATOR_ENABLED` | `true` | Start/stop the simulator |
| `SIMULATOR_TICK_INTERVAL` | `PT1S` | Real-time scheduler tick |
| `SIMULATOR_ACCELERATION` | `1.0` | Multiplier for virtual time (use high values for historical backfills) |
| `SIMULATOR_SEED` | none | Optional deterministic seed for reproducible output |
| `SIMULATOR_ANOMALY_PROBABILITY` | `0.005` | Chance of an anomalous reading |
| `SIMULATOR_KAFKA_TOPIC` | `enerlytics.telemetry.meter-reading-received.v1` | Destination topic |

## Configuration

All runtime and build configuration is driven by environment variables. The
`.env.example` file documents the required variables and uses safe placeholder
values. The real `.env` file is ignored by Git.

| Area | Key files |
|------|-----------|
| Backend | `backend/src/main/resources/application.yml` reads `${VAR:default}` |
| Frontend build | `frontend/scripts/set-env.js` reads `.env` and writes `src/environments/environment.ts` |
| Local infrastructure | `infrastructure/compose/docker-compose.yml` reads the root `.env` |

Never commit secrets, API keys, or production credentials.

## Bootstrap scope

What is currently present:

- A compilable Spring Boot 3.x backend application with Actuator, Web, Validation,
  Security, JPA, Redis, Kafka, Batch, Flyway, PostgreSQL, jjwt, and OpenAPI.
- JWT-based authentication, refresh-token rotation, logout, and RBAC.
- Organization-level tenant isolation with `X-Organization-Id`.
- Flyway-managed identity, organization, facility, meter, and simulation schema.
- Meter registration, lifecycle, location assignment, search, and heartbeat APIs.
- A standalone Spring Boot telemetry simulator that publishes realistic
  `MeterReadingReceived` Kafka events for active simulated meters.
- A compilable Angular 22 application with strict TypeScript, SCSS, and
  Angular Material.
- Dockerfiles for backend and frontend.
- A Docker Compose definition for PostgreSQL 18.6, Apache Kafka 4.3.1, and
  Redis 8.10.1, each with a health check.

The following are intentionally absent and will be added in later phases:

- Telemetry ingestion consumers, validation, persistence, and transactional outbox.
- Energy aggregation, cost, carbon, tariff, forecast, alert, and analytics features.
- Angular dashboards, reports, and user workflows beyond the initial shell.

## Technology versions

| Component | Version | Rationale |
|-----------|---------|-----------|
| Java | 21 | Latest LTS; required by Spring Boot 3.5.x and the project mandate. |
| Spring Boot | 3.5.16 | Current stable 3.x release; avoids Spring Boot 4 until explicitly approved. |
| Maven | 3.9.16 (via wrapper) | Stable build tool. |
| Angular | 22.1.x | Current stable release line; Angular 22.2 is pre-release. |
| TypeScript | ~6.0.2 | Required by Angular 22 strict mode. |
| PostgreSQL | 18.6 | Latest stable major release. |
| Apache Kafka | 4.3.1 | Latest stable release; runs in KRaft mode (no ZooKeeper). |
| Redis | 8.10.1 | Latest stable image. |

## Troubleshooting

- **Maven wrapper fails to download Java dependencies:** Verify internet access
  and that `JAVA_HOME` points to a Java 21 installation.
- **Angular build fails with Node version errors:** Install Node.js 24.15.0 or
  newer, or use `nvm`/`nvm-windows` to switch versions.
- **Docker command not found:** Install Docker Desktop or add the Docker CLI to
  your PATH.
- **Ports already in use:** Edit `.env` to use different `*_PORT` values before
  starting the compose stack.
