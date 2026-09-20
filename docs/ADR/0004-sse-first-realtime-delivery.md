# ADR-0004: Use SSE-First Realtime Browser Delivery

- Status: Accepted
- Date: 2026-09-20
- Decision owners: Architecture and frontend engineering

## Context

The MVP needs near-real-time browser updates for consumption summaries, freshness, alerts, ingestion/report operations, and report completion. Browser-to-server mutations remain ordinary authenticated commands. The required live flows are predominantly one-way and must work through Nginx/Kubernetes Ingress, reconnect safely, enforce tenant/resource scope, and degrade without losing business state.

Raw telemetry volume and Kafka topics are not suitable browser contracts. The browser needs authorized summaries with data quality and freshness, not direct infrastructure access.

## Decision

Use Server-Sent Events (SSE) as the primary MVP server-to-browser update transport. Keep commands and authoritative reads on REST.

The core backend exposes authenticated, tenant/resource-scoped SSE streams. It emits minimal summary-change, alert-lifecycle, operation-status, and report-status events. It never exposes Kafka directly and never sends cross-tenant events for client-side filtering.

Redis may distribute non-authoritative invalidation/update hints among core replicas. After a hint, the core emits an authorized summary or directs the client to refetch authoritative state. PostgreSQL remains authoritative.

Streams support heartbeats, connection limits, proxy-safe timeouts, bounded event IDs/replay where practical, and exponential reconnect with jitter. If the resume point is outside the retained window, the client performs REST resynchronization. Polling with explicit freshness is the fallback.

## Alternatives Considered

### WebSocket

Deferred because MVP communication is one-way and commands do not require a persistent bidirectional channel. WebSocket adds connection-state, protocol, proxy, authorization, and operational complexity without a current product need. Reconsider for genuine high-frequency bidirectional control/collaboration.

### Polling only

Rejected as the primary strategy because it creates avoidable repeated load and delays alert/operation updates. Retained as a degraded fallback.

### Browser consumes Kafka

Rejected because it exposes internal contracts/infrastructure, cannot safely enforce product authorization at the required resource level, and couples UI lifecycle to event topology.

### Redis as durable realtime event store

Rejected. Redis hints improve replica fan-out but must not become authoritative or required for durable business outcomes.

## Consequences

### Positive

- Simple one-way HTTP semantics and broad proxy/browser support.
- Native reconnect behavior and straightforward observability.
- Commands remain explicit, idempotent REST operations.
- No direct coupling between browsers and Kafka.
- Graceful fallback to polling/resynchronization.

### Negative / Risks

- Browser per-origin connection constraints and many concurrent streams require testing.
- SSE uses text framing and is not appropriate for high-volume raw telemetry.
- Authorization changes must terminate or re-evaluate active streams promptly.
- Multi-replica fan-out needs ephemeral coordination or each replica must observe relevant events.
- Loss beyond the bounded replay window requires full refetch.

## Validation and Follow-up

- Load-test expected concurrent connections, event rates, ingress buffering/timeouts, and reconnect storms.
- Test tenant/resource isolation, role revocation, token expiry, resume, duplicate event handling, and fallback polling.
- Monitor active connections, disconnects, reconnect rate, event delay, dropped/resync count, and per-tenant limits without high-cardinality metrics.
- Record a new ADR if bidirectional product requirements justify WebSocket.
