# ADR-0002: Initial Deployment Topology and Service Boundaries

- Status: Accepted
- Date: 2026-09-20
- Decision owners: Architecture and engineering
- Supersedes: ADR-0001

## Context

The functional specification establishes a multi-tenant platform with interactive administration and analytics, bursty telemetry ingestion, deterministic calculations, asynchronous reports, notifications, and operational monitoring. Expected production scale is not yet approved. Starting with one service per domain would create distributed consistency, authorization, deployment, and observability costs without evidence.

ADR-0001 selected one modular-monolith backend deployable. Detailed flow analysis now shows that telemetry processing has scaling and failure characteristics materially different from synchronous user traffic: it is bursty, write-heavy, Kafka-lag-driven, replayable, and can consume CPU without being on an interactive request path.

The platform also needs an external boundary. A dedicated application gateway is useful when independently routed APIs require centralized transformation or policy, but initially Enerlytics has one public application API.

## Decision

Build a domain-oriented modular backend from one Java/Spring Boot codebase and release train. Enforce internal boundaries for `identity`, `organization`, `site`, `meter`, `telemetry`, `energy`, `carbon`, `tariff`, `analytics`, `forecast`, `alert`, `reporting`, `notification`, and `audit`.

Run the backend artifact in two initial production roles:

1. **Core application:** REST/SSE APIs, authorization, configuration, analytical queries, orchestration, jobs, and most event consumers.
2. **Telemetry worker:** Kafka-driven telemetry validation, idempotent persistence, quality/freshness processing, and telemetry-derived events.

The telemetry role may be co-located with core for local development or low-volume environments. Separate production deployment is workload isolation, not a separately owned business microservice. Both roles use the same contracts and PostgreSQL system of record; modules still own their tables.

Use Nginx or Kubernetes Ingress as the initial edge boundary for TLS, routing, static assets, request limits, and coarse rate limiting. Do not add a dedicated API gateway service initially. Authentication, tenant/resource authorization, validation, and business policy remain in the backend.

Domain modules are not separately deployed until measured scaling, availability, security, data sovereignty, ownership, or release-cadence evidence justifies extraction and a new ADR defines data ownership and migration.

## Alternatives Considered

### One backend process for every environment

Rejected for production because telemetry backlog or replay could exhaust resources needed for interactive APIs and cannot be scaled independently. It remains acceptable for development and proven low-volume installations.

### One microservice per domain module

Rejected because it would require distributed transactions or sagas, duplicated security/policy enforcement, independent deployments, and larger operational burden before boundaries and scale are proven.

### Dedicated API gateway service

Deferred because there is one public backend API. Nginx/Ingress provides the required north-south controls without duplicating application authorization or adding a network hop. Reconsider when multiple independently deployed public APIs need routing, protocol translation, consumer-specific policy, or lifecycle isolation.

### Telemetry as a fully independent service and database

Deferred. Separate processing role gives workload isolation without creating cross-service meter configuration, authorization, and consistency problems. Reconsider when telemetry write scale, retention, datastore specialization, availability, or team ownership requires it.

## Consequences

### Positive

- Preserves simple domain transactions and one coherent security model.
- Protects interactive traffic from telemetry bursts and allows Kafka-lag-based scaling.
- Avoids unnecessary gateway and microservice complexity.
- Keeps extraction options through module contracts, events, table ownership, and separate runtime profiles.
- Uses one release artifact while allowing operational isolation.

### Negative / Risks

- Shared database and release train can still couple modules.
- Runtime profiles require careful adapter/consumer activation and integration testing.
- Telemetry worker needs efficient, versioned access to meter configuration without reading private module tables arbitrarily.
- Core remains a larger blast radius than independently deployed domains.
- Ingress capability differences across environments need standardized configuration.

## Validation and Follow-up

- Add architecture tests for module dependencies and repository ownership.
- Add deployment-profile tests proving only intended endpoints/consumers start in each role.
- Load-test core and telemetry roles independently using approved volume assumptions.
- Monitor module resource use, Kafka lag, latency, failure rates, and release friction.
- Require a new ADR before any domain service/database extraction or dedicated API gateway.
