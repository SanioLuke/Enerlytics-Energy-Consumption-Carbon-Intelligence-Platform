# Architecture Decision Records

Architecture Decision Records capture consequential decisions, their context, alternatives, and consequences.

## Process

1. Copy the structure below into the next zero-padded file: `NNNN-short-title.md`.
2. Use status `Proposed`, `Accepted`, `Superseded`, or `Rejected`.
3. Link superseding and superseded records.
4. Keep accepted records immutable except for status and cross-references; create a new ADR to change a decision.
5. Reference affected contracts, migrations, operational controls, and tests.

## Template

```markdown
# ADR-NNNN: Decision title

- Status: Proposed
- Date: YYYY-MM-DD
- Decision owners: role or team

## Context

## Decision

## Alternatives Considered

## Consequences

### Positive

### Negative / Risks

## Validation and Follow-up
```

## Index

- [ADR-0001: Start as a Domain-Oriented Modular Monolith](0001-modular-monolith.md) — Superseded by ADR-0002
- [ADR-0002: Initial Deployment Topology and Service Boundaries](0002-deployment-topology-and-service-boundaries.md) — Accepted
- [ADR-0003: Kafka, Outbox/Inbox, and Telemetry Processing Guarantees](0003-kafka-outbox-and-telemetry-guarantees.md) — Accepted
- [ADR-0004: Use SSE-First Realtime Browser Delivery](0004-sse-first-realtime-delivery.md) — Accepted
