# Enerlytics

A production-grade Energy Consumption & Carbon Intelligence Platform.

This repository is currently in the **bootstrap phase**. It contains the technical
foundation for the Angular frontend, Java/Spring Boot backend, and local
infrastructure services (PostgreSQL, Kafka, Redis). No business features have
been implemented yet.

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

6. **Build and test the frontend:**

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

This phase intentionally does **not** implement business functionality. The
following are intentionally absent and will be added in later phases:

- Domain entities, JPA repositories, and business REST endpoints
- Kafka producers/consumers and telemetry processing logic
- Energy, cost, carbon, tariff, forecast, alert, and analytics features
- Angular dashboards, reports, and user workflows
- Flyway database migrations (Flyway is on the classpath but disabled by default)

Only the following technical scaffolding is present:

- A compilable Spring Boot 3.x application with Actuator, Web, Validation,
  Security, JPA, Redis, Kafka, Batch, Flyway, and PostgreSQL dependencies.
- A compilable Angular 22 application with strict TypeScript, SCSS, and
  Angular Material.
- Dockerfiles for backend and frontend.
- A Docker Compose definition for PostgreSQL 18.6, Apache Kafka 4.3.1, and
  Redis 8.10.1, each with a health check.

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
