CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE IF NOT EXISTS billing.tariff (
    id               UUID PRIMARY KEY,
    organization_id  UUID NOT NULL REFERENCES org.organization(id),
    site_id          UUID NOT NULL REFERENCES org.site(id),
    name             VARCHAR(200) NOT NULL,
    tariff_type      VARCHAR(16) NOT NULL,
    currency         CHAR(3) NOT NULL,
    timezone         VARCHAR(100) NOT NULL,
    effective_from   DATE NOT NULL,
    effective_to     DATE,
    active           BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_tariff_type
        CHECK (tariff_type IN ('FLAT_RATE', 'TIME_OF_USE')),
    CONSTRAINT chk_tariff_effective_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_tariff_site_effective
    ON billing.tariff(site_id, effective_from);

CREATE TABLE IF NOT EXISTS billing.tariff_rate (
    id                  UUID PRIMARY KEY,
    tariff_id           UUID NOT NULL REFERENCES billing.tariff(id) ON DELETE CASCADE,
    day_type            VARCHAR(16) NOT NULL,
    start_time          TIME NOT NULL,
    end_time            TIME NOT NULL,
    cost_per_kwh        DECIMAL(20, 9) NOT NULL CHECK (cost_per_kwh >= 0),
    demand_rate_per_kw  DECIMAL(20, 9) CHECK (demand_rate_per_kw >= 0),
    priority            INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_rate_day_type
        CHECK (day_type IN ('ALL', 'WEEKDAY', 'WEEKEND')),
    CONSTRAINT chk_rate_window
        CHECK (start_time <> end_time)
);

CREATE INDEX IF NOT EXISTS idx_tariff_rate_tariff
    ON billing.tariff_rate(tariff_id);

CREATE TABLE IF NOT EXISTS billing.energy_cost (
    id                   UUID PRIMARY KEY,
    organization_id      UUID NOT NULL REFERENCES org.organization(id),
    dimension_type       VARCHAR(16) NOT NULL,
    dimension_id         UUID NOT NULL,
    granularity          VARCHAR(16) NOT NULL,
    bucket_start         TIMESTAMP WITH TIME ZONE NOT NULL,
    bucket_end           TIMESTAMP WITH TIME ZONE NOT NULL,
    tariff_id            UUID,
    currency             CHAR(3),
    energy_consumed_kwh  DECIMAL(24, 9) NOT NULL DEFAULT 0,
    energy_cost          DECIMAL(24, 9),
    demand_charge        DECIMAL(24, 9),
    total_cost           DECIMAL(24, 9),
    coverage_ratio       DECIMAL(9, 6) NOT NULL DEFAULT 0,
    quality_status       VARCHAR(16) NOT NULL DEFAULT 'UNAVAILABLE',
    missing_rate_hours   INT NOT NULL DEFAULT 0,
    calculation_run_id   UUID NOT NULL,
    computed_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_cost_granularity
        CHECK (granularity IN ('HOUR', 'DAY', 'MONTH')),
    CONSTRAINT chk_cost_dimension
        CHECK (dimension_type IN ('METER', 'ZONE', 'BUILDING', 'SITE', 'ORGANIZATION')),
    CONSTRAINT chk_cost_quality
        CHECK (quality_status IN ('COMPLETE', 'PARTIAL', 'UNAVAILABLE')),
    CONSTRAINT uq_energy_cost UNIQUE
        (organization_id, dimension_type, dimension_id, granularity, bucket_start)
);

CREATE INDEX IF NOT EXISTS idx_energy_cost_lookup
    ON billing.energy_cost(organization_id, dimension_type, dimension_id, granularity, bucket_start);

CREATE INDEX IF NOT EXISTS idx_energy_cost_computed_at
    ON billing.energy_cost(computed_at);
