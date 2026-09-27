# Energy Aggregation

## Overview

Enerlytics materializes energy analytics in `analytics.energy_aggregate`. Dashboard requests query these precomputed rows and never aggregate `telemetry.meter_reading` at request time.

Buckets use half-open UTC intervals `[bucketStart, bucketEnd)`:

- `QUARTER_HOUR`: 00, 15, 30, and 45 minutes
- `HOUR`: UTC clock hour
- `DAY`: UTC calendar day
- `MONTH`: UTC calendar month

UTC makes results reproducible across deployments and daylight-saving transitions. Site-local calendar boundaries are a future reporting enhancement.

## Dimensions and metrics

Every bucket is independently materialized for `METER`, `ZONE`, `BUILDING`, `SITE`, and `ORGANIZATION`. Nullable hierarchy levels are omitted for meters not assigned to a building or zone.

Stored metrics are:

- `energyConsumedKwh`: sum of `energy_kwh`
- `averagePowerKw`: average of non-null `power_kw`
- `peakPowerKw`: maximum non-null `power_kw`
- `minimumPowerKw`: minimum non-null `power_kw`
- `averagePowerFactor`: average of non-null `power_factor`
- `readingCount`: number of accepted readings
- `estimatedReadingCount`: expected number of readings, computed from bucket duration and each included meter's configured reading interval
- `dataCompletenessPercentage`: `min(readingCount / estimatedReadingCount * 100, 100)`, rounded half-up to two decimals

Power and power-factor metrics remain null when no source reading supplies the measurement. Energy and counts are zero for an explicitly rebuilt empty bucket.

## Incremental processing

`EnergyAggregationConsumer` consumes `enerlytics.telemetry.meter-reading-validated.v1` with manual acknowledgment. For each validated reading it resolves the meter hierarchy and recomputes the affected bucket for every granularity and applicable dimension. Kafka is acknowledged only after the database transaction succeeds; transient failures therefore cause redelivery.

The update is recompute-based rather than additive. Each bucket is calculated from authoritative readings in its bounded time interval and replaces the previous result. Consequently:

- replaying an event does not double-count it;
- duplicate transport delivery converges to the same values;
- late or out-of-order readings revise the affected buckets;
- historical buckets can be reproduced from source readings;
- correction and backfill workflows do not need inverse deltas.

The unique key `(dimension_type, dimension_id, granularity, bucket_start)` prevents duplicate materialized buckets. Concurrent first writers can cause one transaction to retry through Kafka; the retried recomputation converges to the authoritative result.

## Reconciliation

`EnergyReconciliationJob` runs every five minutes by default and examines readings whose `created_at` falls in a configurable lookback window. It groups those rows by meter and obtains only each meter's affected event-time range. It then rebuilds intersecting buckets for the meter and its current hierarchy.

Configuration:

| Environment variable | Default | Purpose |
|---|---:|---|
| `KAFKA_AGGREGATION_GROUP_ID` | `enerlytics-energy-aggregation` | Independent aggregation consumer group |
| `AGGREGATION_CONSUMER_ENABLED` | `true` | Enables validated-reading aggregation consumer |
| `AGGREGATION_RECONCILE_INTERVAL` | `PT5M` | Delay between reconciliation passes |
| `AGGREGATION_RECONCILE_INITIAL_DELAY` | `PT1M` | Startup delay |
| `AGGREGATION_RECONCILE_LOOKBACK` | `PT24H` | Ingestion-time late-data window |

Historical recalculation uses the same `recomputeBucket` operation over a bounded range. The source indexes used are `meter_reading(meter_id, sample_timestamp)`, `meter_reading(organization_id, sample_timestamp)`, and `meter_reading(created_at)`.

## API

```text
GET /api/v1/organizations/{orgId}/analytics/energy
```

Required permission: `analytics:read`.

Query parameters:

- `dimension`: `METER`, `ZONE`, `BUILDING`, `SITE`, or `ORGANIZATION`; defaults to `ORGANIZATION`
- `dimensionId`: required except for organization queries, where it defaults to `{orgId}`
- `granularity`: `QUARTER_HOUR`, `HOUR`, `DAY`, or `MONTH`; defaults to `HOUR`
- `from`: inclusive RFC 3339 timestamp
- `to`: exclusive RFC 3339 timestamp

The service validates that the requested dimension belongs to the authenticated organization and returns buckets ordered by `bucketStart`. Results are bounded to 10,000 buckets per request.

## Query strategy

The dashboard read predicate is:

```sql
WHERE organization_id = ?
  AND dimension_type = ?
  AND dimension_id = ?
  AND granularity = ?
  AND bucket_start BETWEEN ? AND ?
ORDER BY bucket_start
```

It is served by `idx_energy_aggregate_read_path` on `(organization_id, dimension_type, dimension_id, granularity, bucket_start)`. No dashboard query joins or scans `meter_reading`.

Recomputation SQL always supplies organization, dimension, `sample_timestamp >= bucketStart`, and `sample_timestamp < bucketEnd`. Reconciliation first narrows candidates using indexed `created_at` and never performs an unbounded scheduled rebuild.

## Representative benchmark

`EnergyAggregationBenchmarkTest` is repeatable and uses H2 2.3 in PostgreSQL compatibility mode:

- 1 organization
- 1 site
- 100 meters
- 14 days
- 15-minute cadence
- 134,400 raw readings
- 336 organization-level hourly aggregate rows

Observed on the development Windows host on 2026-09-27:

| Operation | Result |
|---|---:|
| Bulk test-data insert | 39,367 ms |
| Organization monthly recompute | 2,757 ms |
| Site monthly recompute | 1,051 ms |
| Meter monthly recompute | 41 ms |
| Incremental event update (all applicable buckets) | 2,497 ms |
| Rebuild 336 organization hourly buckets | 6,605 ms total / 19 ms average |
| Read 336 precomputed hourly buckets | 40 ms |
| Empty bounded aggregate read | 51 ms |

These values include H2/JPA test overhead and are not production PostgreSQL service-level objectives. Before production launch, run `EXPLAIN (ANALYZE, BUFFERS)` on PostgreSQL with production-scale cardinality, verify index scans, and establish p95/p99 targets. The benchmark's architectural result is that API latency scales with returned aggregate buckets rather than raw-reading volume.

## Known limitations

- Day and month boundaries are UTC rather than site-local.
- Reconciliation uses the meter's current hierarchy; effective-dated location history is not modeled yet.
- The current incremental strategy favors correctness and replay safety over minimum write amplification: one reading may rebuild up to 20 buckets.
- Aggregate update events are not emitted yet; carbon processing can initially consume persisted aggregates or add an aggregation outbox in its implementation phase.
