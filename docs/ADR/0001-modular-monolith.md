# ADR-0001: Start as a Domain-Oriented Modular Monolith

- Status: Superseded
- Date: 2026-09-20
- Decision owners: Architecture and engineering
- Superseded by: ADR-0002

## Context

Enerlytics must support energy and carbon accounting, high-volume ingestion, analytics, reporting, and secure tenant isolation. The repository begins empty and product boundaries and operational scale are not yet proven. Independent microservices would immediately add distributed transactions, deployment coordination, duplicated platform concerns, and higher operational cost.

The required technology baseline includes Spring Boot, Spring Cloud, Kafka, Redis, PostgreSQL, and Angular, but it explicitly prohibits unnecessary microservices.

## Decision

Build one Spring Boot backend deployable organized as strongly bounded domain modules. Apply clean architecture within modules where it provides meaningful separation. Expose versioned, contract-first HTTP APIs to Angular. Use Kafka for durable asynchronous workloads and integration events where decoupling, buffering, or replay is beneficial. Use Spring Batch for restartable bulk work.

Modules do not access another module's repositories or persistence entities directly. They collaborate through application interfaces or stable events. PostgreSQL remains the system of record. Event publication uses a transactional outbox, and consumers are idempotent.

Spring Cloud components are adopted only for demonstrated needs; the dependency baseline does not require service discovery or distributed configuration in the initial single-backend deployment.

## Alternatives Considered

### Microservices from inception

Rejected because boundaries, team topology, scale, and independent deployment requirements are not established. Operational and consistency costs exceed demonstrated benefit.

### Unstructured layered monolith

Rejected because global controller/service/repository layers encourage coupling and make future ownership or extraction difficult.

### Serverless functions as the primary architecture

Rejected as the primary model because calculation, batch, streaming, and transactional requirements benefit from a cohesive domain runtime. Targeted managed/serverless infrastructure may still be selected later.

## Consequences

### Positive

- Simple local development, transactions, testing, deployment, and observability.
- Explicit domain ownership without distributed-system overhead.
- Kafka and port boundaries preserve paths for later extraction.
- Infrastructure can scale the backend horizontally before service decomposition.

### Negative / Risks

- Module boundaries require automated enforcement and disciplined ownership.
- One deployment couples release cadence and may have a larger blast radius.
- Heavy ingestion or calculation workloads may eventually need independent scaling.
- Shared database access can erode boundaries if schemas and repository ownership are not controlled.

## Validation and Follow-up

- Add architecture tests that reject forbidden module dependencies.
- Track module-specific latency, throughput, resource use, failure rates, and release friction.
- Consider extraction only when evidence shows independent scaling, availability, security, ownership, or release requirements.
- Record any extraction or architectural replacement in a superseding ADR.
