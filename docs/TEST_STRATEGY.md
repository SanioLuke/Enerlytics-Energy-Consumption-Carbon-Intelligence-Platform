# Test Strategy

## Objectives

Tests provide evidence that calculations are deterministic, tenant boundaries are enforced, contracts remain compatible, migrations work, and deployments are operable. Core business logic is exercised without mocking the logic itself.

## Test Layers

### Backend

- **Domain unit tests:** value objects, unit conversion, rounding, tariff, energy, and carbon policies; fast and framework-free.
- **Application tests:** use-case orchestration and authorization with ports replaced by controlled test doubles only at external boundaries.
- **Persistence/integration tests:** PostgreSQL, Redis, Kafka, Flyway, serialization, outbox, idempotency, and transaction behavior using real containerized dependencies where appropriate.
- **API tests:** OpenAPI conformance, validation, errors, pagination, filtering, sorting, concurrency, and authorization.
- **Batch tests:** restartability, chunk boundaries, duplicate input, partial failure, and reconciliation.

### Frontend

- Unit tests for components, pipes, services, state transitions, and chart adapters.
- Accessibility tests for semantics, keyboard operation, focus, names, and contrast-sensitive states.
- Integration tests for API interaction and error/loading/empty states.
- End-to-end tests for critical user journeys on representative desktop, tablet, and mobile viewports.

### Non-Functional

- Performance and volume tests for ingestion, queries, aggregation, and Kafka lag.
- Resilience tests for retries, timeouts, unavailable dependencies, redelivery, and recovery.
- Security tests for authentication, authorization, tenant isolation, injection, unsafe exports, and rate limits.
- Migration tests from the latest supported production schema and restore/recovery exercises.

## Calculation Assurance

- Use authoritative fixtures with units, timezone transitions, missing/duplicate intervals, negative generation values, decimal boundaries, and factor validity boundaries.
- Property-based tests should verify conversion invariants and aggregation consistency where beneficial.
- Golden datasets must identify source, assumptions, algorithm version, factor version, expected precision, and rounding mode.
- Recalculation must be reproducible from persisted provenance.

## Quality Gates

A phase is not complete unless relevant code compiles and applicable tests, linting, formatting, type checking, migration validation, and production builds pass. Failures are fixed rather than waived without a documented, approved reason. Changed behavior requires changed tests.

CI should progress from fast checks to integration, end-to-end, security, image, and deployment validation. Coverage informs risk but is not a substitute for meaningful assertions; thresholds will be established after bootstrap and baseline measurement.

## Test Data

Use deterministic synthetic data. Never copy production secrets or personal/operational data into tests. Builders and fixtures preserve valid defaults while making tested differences explicit. Tests must be parallel-safe and independent of execution order.
