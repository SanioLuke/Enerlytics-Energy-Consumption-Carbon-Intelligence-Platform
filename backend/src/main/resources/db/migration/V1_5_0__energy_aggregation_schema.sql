CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.energy_aggregate (
    id                       UUID PRIMARY KEY,
    organization_id          UUID NOT NULL REFERENCES org.organization(id),
    dimension_type           VARCHAR(16) NOT NULL,
    dimension_id             UUID NOT NULL,
    granularity              VARCHAR(16) NOT NULL,
    bucket_start             TIMESTAMP WITH TIME ZONE NOT NULL,
    bucket_end               TIMESTAMP WITH TIME ZONE NOT NULL,
    energy_consumed_kwh      DECIMAL(18, 9) NOT NULL DEFAULT 0,
    average_power_kw         DECIMAL(12, 6),
    peak_power_kw            DECIMAL(12, 6),
    minimum_power_kw         DECIMAL(12, 6),
    average_power_factor     DECIMAL(5, 4),
    reading_count            BIGINT NOT NULL DEFAULT 0,
    estimated_reading_count  BIGINT NOT NULL DEFAULT 0,
    data_completeness_pct    DECIMAL(5, 2) NOT NULL DEFAULT 0,
    computed_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_energy_aggregate_dimension
        CHECK (dimension_type IN ('METER', 'ZONE', 'BUILDING', 'SITE', 'ORGANIZATION')),
    CONSTRAINT chk_energy_aggregate_granularity
        CHECK (granularity IN ('QUARTER_HOUR', 'HOUR', 'DAY', 'MONTH')),
    CONSTRAINT uq_energy_aggregate_bucket
        UNIQUE (dimension_type, dimension_id, granularity, bucket_start)
);

-- Dashboard queries filter by organization + dimension + granularity and
-- range-scan bucket_start. This index serves every analytics read path.
CREATE INDEX IF NOT EXISTS idx_energy_aggregate_read_path
    ON analytics.energy_aggregate(organization_id, dimension_type, dimension_id, granularity, bucket_start);

-- Reconciliation touches rows by computed_at for diagnostics/auditing.
CREATE INDEX IF NOT EXISTS idx_energy_aggregate_computed_at
    ON analytics.energy_aggregate(computed_at);

-- Reconciliation scans meter_reading by ingestion time to find late arrivals.
CREATE INDEX IF NOT EXISTS idx_meter_reading_created_at
    ON telemetry.meter_reading(created_at);
