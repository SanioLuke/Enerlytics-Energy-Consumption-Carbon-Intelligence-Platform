CREATE SCHEMA IF NOT EXISTS carbon;

CREATE TABLE IF NOT EXISTS carbon.emission (
    id                       UUID PRIMARY KEY,
    organization_id          UUID NOT NULL REFERENCES org.organization(id),
    dimension_type           VARCHAR(16) NOT NULL,
    dimension_id             UUID NOT NULL,
    granularity              VARCHAR(16) NOT NULL,
    bucket_start             TIMESTAMP WITH TIME ZONE NOT NULL,
    bucket_end               TIMESTAMP WITH TIME ZONE NOT NULL,
    grid_region_code         VARCHAR(100),
    energy_consumed_kwh      DECIMAL(24, 9) NOT NULL DEFAULT 0,
    carbon_intensity_gco2eq_per_kwh DECIMAL(20, 9),
    carbon_intensity_source  VARCHAR(64),
    emissions_kgco2eq        DECIMAL(24, 9) NOT NULL DEFAULT 0,
    emissions_gco2eq         DECIMAL(24, 9) NOT NULL DEFAULT 0,
    emissions_tco2eq         DECIMAL(24, 9) NOT NULL DEFAULT 0,
    coverage_ratio           DECIMAL(9, 6) NOT NULL DEFAULT 0,
    estimated                BOOLEAN NOT NULL DEFAULT false,
    quality_status           VARCHAR(16) NOT NULL DEFAULT 'UNAVAILABLE',
    missing_factor_hours     INT NOT NULL DEFAULT 0,
    calculation_run_id       UUID NOT NULL,
    computed_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_emission_granularity
        CHECK (granularity IN ('HOUR', 'DAY', 'MONTH')),
    CONSTRAINT chk_emission_dimension
        CHECK (dimension_type IN ('METER', 'ZONE', 'BUILDING', 'SITE', 'ORGANIZATION')),
    CONSTRAINT chk_emission_quality
        CHECK (quality_status IN ('COMPLETE', 'PARTIAL', 'ESTIMATED', 'UNAVAILABLE')),
    CONSTRAINT uq_emission UNIQUE (organization_id, dimension_type, dimension_id, granularity, bucket_start)
);

CREATE INDEX IF NOT EXISTS idx_emission_lookup
    ON carbon.emission(organization_id, dimension_type, dimension_id, granularity, bucket_start);

CREATE INDEX IF NOT EXISTS idx_emission_computed_at
    ON carbon.emission(computed_at);
