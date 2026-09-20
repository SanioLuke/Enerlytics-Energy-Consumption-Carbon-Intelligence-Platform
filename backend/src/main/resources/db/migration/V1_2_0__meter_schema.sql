CREATE SCHEMA IF NOT EXISTS telemetry;

CREATE TABLE IF NOT EXISTS telemetry.meter (
    id                      UUID PRIMARY KEY,
    organization_id         UUID NOT NULL REFERENCES org.organization(id),
    site_id                 UUID NOT NULL REFERENCES org.site(id),
    building_id             UUID REFERENCES org.building(id),
    zone_id                 UUID REFERENCES org.zone(id),
    meter_code              VARCHAR(64) NOT NULL,
    meter_name              VARCHAR(200) NOT NULL,
    serial_number           VARCHAR(100),
    manufacturer            VARCHAR(100),
    model                   VARCHAR(100),
    meter_type              VARCHAR(64),
    installation_date       DATE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PROVISIONING',
    reading_interval_seconds INTEGER NOT NULL,
    unit                    VARCHAR(20) NOT NULL DEFAULT 'kWh',
    last_seen_at            TIMESTAMP WITH TIME ZONE,
    commissioned_at         TIMESTAMP WITH TIME ZONE,
    decommissioned_at       TIMESTAMP WITH TIME ZONE,
    metadata                TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_meter_status CHECK (status IN ('PROVISIONING', 'ACTIVE', 'OFFLINE', 'MAINTENANCE', 'DECOMMISSIONED')),
    CONSTRAINT chk_meter_reading_interval CHECK (reading_interval_seconds > 0),
    CONSTRAINT uq_meter_org_code UNIQUE (organization_id, meter_code)
);

CREATE INDEX IF NOT EXISTS idx_meter_organization ON telemetry.meter(organization_id);
CREATE INDEX IF NOT EXISTS idx_meter_site ON telemetry.meter(site_id);
CREATE INDEX IF NOT EXISTS idx_meter_building ON telemetry.meter(building_id);
CREATE INDEX IF NOT EXISTS idx_meter_zone ON telemetry.meter(zone_id);
CREATE INDEX IF NOT EXISTS idx_meter_status ON telemetry.meter(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_meter_name_search ON telemetry.meter(organization_id, meter_name);
