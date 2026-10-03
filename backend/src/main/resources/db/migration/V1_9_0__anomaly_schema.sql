CREATE SCHEMA IF NOT EXISTS anomaly;

CREATE TABLE IF NOT EXISTS anomaly.maintenance_window (
    id               UUID PRIMARY KEY,
    organization_id  UUID NOT NULL REFERENCES org.organization(id),
    dimension_type   VARCHAR(16) NOT NULL,
    dimension_id     UUID NOT NULL,
    starts_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    reason           VARCHAR(500),
    active           BOOLEAN NOT NULL DEFAULT true,
    created_by       VARCHAR(255),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_window_dim CHECK (dimension_type IN ('METER','ZONE','BUILDING','SITE','ORGANIZATION')),
    CONSTRAINT chk_window_time CHECK (ends_at > starts_at)
);

CREATE INDEX idx_window_lookup ON anomaly.maintenance_window(organization_id, starts_at, ends_at);

CREATE TABLE IF NOT EXISTS anomaly.detected_anomaly (
    id                 UUID PRIMARY KEY,
    organization_id    UUID NOT NULL REFERENCES org.organization(id),
    dimension_type     VARCHAR(16) NOT NULL,
    dimension_id       UUID NOT NULL,
    bucket_start       TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_kwh         DECIMAL(24,9) NOT NULL,
    expected_kwh       DECIMAL(24,9) NOT NULL,
    deviation_pct      DECIMAL(14,6) NOT NULL,
    method             VARCHAR(32) NOT NULL,
    severity           VARCHAR(16) NOT NULL,
    confidence         DECIMAL(5,4) NOT NULL,
    explanation        VARCHAR(1000) NOT NULL,
    suppressed         BOOLEAN NOT NULL DEFAULT false,
    suppression_reason VARCHAR(64),
    calculation_run_id UUID NOT NULL,
    detected_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_anomaly_dim CHECK (dimension_type IN ('METER','ZONE','BUILDING','SITE','ORGANIZATION')),
    CONSTRAINT chk_anomaly_method CHECK (method IN
        ('ROLLING_MEAN_DEVIATION','ROLLING_ZSCORE','SAME_HOUR_BASELINE','PERCENTAGE_DEVIATION')),
    CONSTRAINT chk_anomaly_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_anomaly_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT uq_anomaly UNIQUE (organization_id, dimension_type, dimension_id, bucket_start, method)
);

CREATE INDEX idx_anomaly_lookup ON anomaly.detected_anomaly(organization_id, dimension_type, dimension_id, bucket_start);
CREATE INDEX idx_anomaly_severity ON anomaly.detected_anomaly(organization_id, severity, bucket_start);
