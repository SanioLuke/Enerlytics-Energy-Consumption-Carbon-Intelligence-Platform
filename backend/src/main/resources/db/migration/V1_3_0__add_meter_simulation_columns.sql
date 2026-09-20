ALTER TABLE telemetry.meter
    ADD COLUMN IF NOT EXISTS simulated BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE telemetry.meter
    ADD COLUMN IF NOT EXISTS simulation_profile VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_meter_simulated_status ON telemetry.meter(organization_id, simulated, status);
