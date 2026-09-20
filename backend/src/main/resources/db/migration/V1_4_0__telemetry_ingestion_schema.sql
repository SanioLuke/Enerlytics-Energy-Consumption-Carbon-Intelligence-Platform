CREATE SCHEMA IF NOT EXISTS telemetry;

CREATE TABLE IF NOT EXISTS telemetry.meter_reading (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL,
    meter_id            UUID NOT NULL,
    channel_id          UUID,
    sample_timestamp    TIMESTAMP WITH TIME ZONE NOT NULL,
    energy_kwh          DECIMAL(18, 9) NOT NULL,
    power_kw            DECIMAL(12, 6),
    voltage             DECIMAL(8, 3),
    current             DECIMAL(12, 6),
    power_factor        DECIMAL(5, 4),
    frequency           DECIMAL(6, 3),
    quality_status      VARCHAR(32),
    source_event_id     UUID NOT NULL,
    metadata            TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_meter_reading_organization
        FOREIGN KEY (organization_id) REFERENCES org.organization(id),
    CONSTRAINT fk_meter_reading_meter
        FOREIGN KEY (meter_id) REFERENCES telemetry.meter(id),
    CONSTRAINT chk_meter_reading_energy_nonnegative
        CHECK (energy_kwh >= 0),
    CONSTRAINT uq_meter_reading_source_event
        UNIQUE (source_event_id)
);

CREATE INDEX IF NOT EXISTS idx_meter_reading_meter_ts
    ON telemetry.meter_reading(meter_id, sample_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_meter_reading_org_ts
    ON telemetry.meter_reading(organization_id, sample_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_meter_reading_source_event
    ON telemetry.meter_reading(source_event_id);

CREATE TABLE IF NOT EXISTS telemetry.meter_reading_rejected (
    id                  UUID PRIMARY KEY,
    organization_id     UUID,
    meter_id            UUID,
    source_event_id     UUID NOT NULL,
    raw_payload         TEXT NOT NULL,
    reason_code         VARCHAR(64) NOT NULL,
    reason_detail       TEXT,
    event_timestamp     TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_meter_reading_rejected_source_event
        UNIQUE (source_event_id)
);

CREATE INDEX IF NOT EXISTS idx_meter_reading_rejected_org_created
    ON telemetry.meter_reading_rejected(organization_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_meter_reading_rejected_source_event
    ON telemetry.meter_reading_rejected(source_event_id);

CREATE TABLE IF NOT EXISTS telemetry.outbox (
    id                  UUID PRIMARY KEY,
    topic               VARCHAR(255) NOT NULL,
    partition_key       VARCHAR(255) NOT NULL,
    payload             TEXT NOT NULL,
    headers             TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    published_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON telemetry.outbox(published_at, created_at);
