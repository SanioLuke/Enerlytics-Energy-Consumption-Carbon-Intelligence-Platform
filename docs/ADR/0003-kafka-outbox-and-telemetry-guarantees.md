# ADR-0003: Kafka, Outbox/Inbox, and Telemetry Processing Guarantees

- Status: Accepted
- Date: 2026-09-20
- Decision owners: Architecture, data, and platform engineering

## Context

Enerlytics must ingest simulated and future external telemetry, absorb bursts, preserve relevant ordering, reject duplicates, support replay, trigger downstream calculations, and expose operational lag. Database updates and Kafka publication cannot be made atomically through an ordinary local transaction. Kafka delivery may repeat, consumers may restart, and telemetry sources may retry.

The product requires idempotency and reproducible authoritative data. Claiming end-to-end exactly-once processing from broker configuration would not protect database business effects or external notifications.

## Decision

Use Kafka as the durable asynchronous backbone for accepted ingestion work and published integration events. Treat delivery as **at least once** and implement **effectively-once business effects** through stable identifiers, database uniqueness, idempotent consumers, and transactional outbox/inbox patterns.

Telemetry ingestion:

1. The authenticated API validates envelope, schema, request size, source permission, and rate policy.
2. It publishes `telemetry.received.v1`, keyed by stable tenant/meter/channel identity, and returns `202 Accepted` only after Kafka durability policy is met.
3. Telemetry workers consume in partition order, validate against versioned meter configuration, normalize units/time, and persist reading, quality, provenance, and inbox/event identity transactionally.
4. Database uniqueness classifies retries as duplicates without duplicate business effect.
5. Authoritative state changes and outbox rows commit in one PostgreSQL transaction.
6. The outbox publisher emits accepted/rejected and derived events. Downstream consumers use inbox/event IDs and commit offsets only after durable effect.

Use bounded retry for transient failures. Non-retryable or exhausted poison events go to versioned dead-letter topics with original event metadata, sanitized reason, alerting, and controlled audited replay. Never use an unbounded in-process queue as a substitute for Kafka.

Events include event ID/type/version, occurred time, tenant ID, aggregate ID/version, correlation/causation IDs, producer, and minimal payload. Schemas are contract-first, backward compatible within a version, and exclude secrets and unnecessary personal data.

## Alternatives Considered

### Direct synchronous persistence from every telemetry source

Rejected as the sole ingestion path because bursts and database outages would couple sources to processing capacity and offer poor replay/backpressure isolation.

### Kafka transactions as the only exactly-once mechanism

Rejected because Kafka transactions do not by themselves make PostgreSQL updates, report artifacts, notifications, or provider calls exactly once.

### Dual write to PostgreSQL and Kafka

Rejected because either side can commit while the other fails, producing missing or phantom events.

### Redis streams or an in-memory queue

Rejected as the durable primary ingestion backbone because Kafka is the mandated durable event platform and provides stronger partitioned replay and consumer-group operations for this workload. Redis remains ephemeral support infrastructure.

## Consequences

### Positive

- Durable backpressure and independent telemetry-worker scaling.
- Deterministic duplicate handling across source and consumer retries.
- Reliable publication of events derived from database transactions.
- Replay and correction support with observable lag and failure queues.
- Explicit semantics avoid misleading exactly-once claims.

### Negative / Risks

- Outbox/inbox tables, publishers, cleanup, and monitoring add complexity.
- Partition-key changes and hot meters can create skew.
- Consumers must be designed for idempotency and event evolution.
- Accepted-for-processing differs from accepted-as-authoritative and must be clear to clients.
- Controlled DLQ replay requires tooling, authorization, and audit.

## Validation and Follow-up

- Contract-test event schemas and compatibility.
- Integration-test producer failure, redelivery, worker crash before/after commit, duplicate source IDs, outbox retry, poison events, and replay.
- Load-test partition distribution, lag recovery, and PostgreSQL write capacity.
- Alert on oldest event age, consumer lag, outbox backlog, rejection rate, DLQ growth, and duplicate anomalies.
- Define Kafka retention, durability acknowledgements, schema governance, and disaster recovery after production scale/RPO decisions.
