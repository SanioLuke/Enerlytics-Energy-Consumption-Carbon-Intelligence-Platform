# Deployment

## Environments

Expected environments are local development, CI, non-production, and production. Configuration is supplied through environment variables and platform configuration. Secrets are sourced from an approved secret store and never committed.

## Local Development

Docker Compose will provide PostgreSQL, Redis, Kafka, Prometheus, and Grafana with health checks and persistent named volumes where useful. Backend and frontend may run on the host for rapid iteration. Example configuration contains safe, non-production values only.

## Build Artifacts

- Maven produces a tested Spring Boot artifact and a minimal, non-root backend image using Java 21.
- Angular production compilation produces immutable static assets served by Nginx.
- Images use pinned base versions, are scanned, labeled with commit/build identity, and are never rebuilt differently for each environment.
- CI produces test reports, an SBOM, provenance where supported, and deployment artifacts.

## Kubernetes

Kubernetes manifests or Helm will define:

- Backend and frontend workloads and services.
- ConfigMaps and references to externally managed Secrets.
- Startup, readiness, and liveness probes with distinct purposes.
- Resource requests/limits, pod disruption controls, security contexts, and network policies.
- Horizontal scaling based on measured signals; consumers also consider lag.
- Ingress through Nginx or the platform ingress layer with TLS.
- Migration execution as a controlled pre-deployment job; application replicas do not race schema changes.

PostgreSQL, Kafka, and Redis should use managed offerings or dedicated operational deployments for production rather than being bundled casually with application pods.

## Delivery Pipeline

GitHub Actions should perform checkout, dependency/cache setup, secret scan, backend compile/test, frontend lint/test/build, contract checks, integration tests, dependency and image scans, image publication, manifest validation, and environment deployment with approvals appropriate to risk. Production promotion uses the same immutable image tested earlier.

## Release and Rollback

- Prefer backward-compatible expand/migrate/contract database changes.
- Deployments use rolling or canary behavior after readiness succeeds.
- Rollback reuses the previous immutable image; database changes must remain compatible with that image during the rollback window.
- Feature flags may decouple deployment from release but must have ownership and removal criteria.
- Changelog and project state are updated for every phase/release.

## Observability and Operations

Prometheus gathers application and infrastructure metrics; Grafana provides operational dashboards. OpenTelemetry exports traces and correlates them with structured logs. Alerts cover availability, latency, error rates, saturation, consumer lag, ingestion rejection, failed batches, and calculation anomalies. Health endpoints reveal no secrets and distinguish readiness from liveness.

## Recovery

Production requires automated encrypted backups, verified restore procedures, defined retention, and agreed RPO/RTO. Disaster recovery, multi-region needs, and data residency remain open until business requirements are approved.
