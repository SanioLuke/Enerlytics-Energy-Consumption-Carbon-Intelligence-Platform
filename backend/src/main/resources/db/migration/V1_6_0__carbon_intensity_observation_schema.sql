CREATE SCHEMA IF NOT EXISTS carbon;

CREATE TABLE IF NOT EXISTS carbon.intensity_observation (
    id                       UUID PRIMARY KEY,
    provider                 VARCHAR(64) NOT NULL,
    zone                     VARCHAR(32) NOT NULL,
    observed_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    carbon_intensity_gco2eq_per_kwh INT NOT NULL,
    estimated                BOOLEAN NOT NULL DEFAULT false,
    retrieved_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_intensity_observation UNIQUE (provider, zone, observed_at)
);

CREATE INDEX IF NOT EXISTS idx_intensity_observation_lookup
    ON carbon.intensity_observation(provider, zone, observed_at DESC);

CREATE INDEX IF NOT EXISTS idx_intensity_observation_retrieved
    ON carbon.intensity_observation(retrieved_at);
