CREATE SCHEMA IF NOT EXISTS forecast;

CREATE TABLE IF NOT EXISTS forecast.forecast_run (
    id               UUID PRIMARY KEY,
    organization_id  UUID NOT NULL REFERENCES org.organization(id),
    dimension_type   VARCHAR(16) NOT NULL,
    dimension_id     UUID NOT NULL,
    horizon          VARCHAR(16) NOT NULL,
    method           VARCHAR(32) NOT NULL,
    generated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    history_from     TIMESTAMP WITH TIME ZONE NOT NULL,
    history_to       TIMESTAMP WITH TIME ZONE NOT NULL,
    mae_kwh          DECIMAL(24,9),
    mape_pct         DECIMAL(14,6),
    evaluated_at     TIMESTAMP WITH TIME ZONE,
    evaluated_points INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_forecast_run_dim CHECK (dimension_type IN ('METER','ZONE','BUILDING','SITE','ORGANIZATION')),
    CONSTRAINT chk_forecast_run_horizon CHECK (horizon IN ('NEXT_24_HOURS','NEXT_7_DAYS')),
    CONSTRAINT chk_forecast_run_method CHECK (method IN
        ('SEASONAL_MOVING_AVERAGE','SAME_HOUR_BASELINE','TREND_ADJUSTED','EXTERNAL_MODEL'))
);

CREATE INDEX idx_forecast_run_lookup ON forecast.forecast_run
    (organization_id, dimension_type, dimension_id, horizon, generated_at DESC);

CREATE TABLE IF NOT EXISTS forecast.forecast_point (
    id               UUID PRIMARY KEY,
    run_id           UUID NOT NULL REFERENCES forecast.forecast_run(id) ON DELETE CASCADE,
    organization_id  UUID NOT NULL,
    bucket_start     TIMESTAMP WITH TIME ZONE NOT NULL,
    predicted_kwh    DECIMAL(24,9) NOT NULL,
    lower_bound_kwh  DECIMAL(24,9) NOT NULL,
    upper_bound_kwh  DECIMAL(24,9) NOT NULL,
    actual_kwh       DECIMAL(24,9),
    absolute_error   DECIMAL(24,9),
    pct_error        DECIMAL(14,6),
    explanation      VARCHAR(1000) NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_forecast_point UNIQUE (run_id, bucket_start),
    CONSTRAINT chk_forecast_point_bounds CHECK (upper_bound_kwh >= lower_bound_kwh)
);

CREATE INDEX idx_forecast_point_run ON forecast.forecast_point (run_id, bucket_start);
