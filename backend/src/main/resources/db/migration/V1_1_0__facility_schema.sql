CREATE TABLE IF NOT EXISTS org.site (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES org.organization(id),
    site_code       VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    address         VARCHAR(255),
    country         VARCHAR(2),
    state           VARCHAR(100),
    city            VARCHAR(100),
    postal_code     VARCHAR(20),
    latitude        NUMERIC(9,6),
    longitude       NUMERIC(9,6),
    iana_timezone   VARCHAR(100) NOT NULL,
    grid_region_code VARCHAR(100),
    currency        CHAR(3) NOT NULL DEFAULT 'USD',
    floor_area      NUMERIC(18,4),
    floor_area_unit VARCHAR(16) DEFAULT 'M2',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    opened_on       DATE,
    closed_on       DATE,
    archived_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_site_latitude CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT chk_site_longitude CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)),
    CONSTRAINT chk_site_floor_area CHECK (floor_area IS NULL OR floor_area > 0),
    CONSTRAINT chk_site_dates CHECK (closed_on IS NULL OR closed_on >= opened_on),
    CONSTRAINT uq_site_organization_code UNIQUE (organization_id, site_code)
);

CREATE INDEX IF NOT EXISTS idx_site_organization ON org.site(organization_id);
CREATE INDEX IF NOT EXISTS idx_site_active ON org.site(organization_id, active);

CREATE TABLE IF NOT EXISTS org.building (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES org.organization(id),
    site_id         UUID NOT NULL REFERENCES org.site(id),
    building_code   VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    floor_area      NUMERIC(18,4),
    floor_area_unit VARCHAR(16) DEFAULT 'M2',
    building_type   VARCHAR(64),
    commissioned_date DATE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    archived_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_building_floor_area CHECK (floor_area IS NULL OR floor_area > 0),
    CONSTRAINT uq_building_site_code UNIQUE (organization_id, site_id, building_code)
);

CREATE INDEX IF NOT EXISTS idx_building_organization ON org.building(organization_id);
CREATE INDEX IF NOT EXISTS idx_building_site ON org.building(site_id);

CREATE TABLE IF NOT EXISTS org.zone (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES org.organization(id),
    site_id         UUID NOT NULL REFERENCES org.site(id),
    building_id     UUID REFERENCES org.building(id),
    zone_code       VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    zone_type       VARCHAR(64),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    archived_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_zone_site_code UNIQUE (organization_id, site_id, zone_code)
);

CREATE INDEX IF NOT EXISTS idx_zone_organization ON org.zone(organization_id);
CREATE INDEX IF NOT EXISTS idx_zone_building ON org.zone(building_id);
CREATE INDEX IF NOT EXISTS idx_zone_site ON org.zone(site_id);
