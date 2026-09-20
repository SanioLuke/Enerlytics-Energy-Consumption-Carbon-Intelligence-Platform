CREATE SCHEMA IF NOT EXISTS org;
CREATE SCHEMA IF NOT EXISTS iam;

-- -----------------------------------------------------------------------------
-- Organization
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS org.organization (
    id              UUID PRIMARY KEY,
    organization_key VARCHAR(64) NOT NULL,
    display_name     VARCHAR(200) NOT NULL,
    status           VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    default_currency CHAR(3) NOT NULL DEFAULT 'USD',
    fiscal_year_start_month SMALLINT NOT NULL DEFAULT 1,
    locale           VARCHAR(35) NOT NULL DEFAULT 'en-US',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at      TIMESTAMP WITH TIME ZONE,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_organization_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT chk_organization_fiscal_month CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    CONSTRAINT uq_organization_key UNIQUE (organization_key),
    CONSTRAINT chk_organization_archived_consistency CHECK (
        (status = 'ARCHIVED' AND archived_at IS NOT NULL) OR (status <> 'ARCHIVED' AND archived_at IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_organization_status ON org.organization(status);

-- -----------------------------------------------------------------------------
-- Users (identities are global; tenant membership is managed separately)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iam.app_user (
    id                UUID PRIMARY KEY,
    identity_provider VARCHAR(100) NOT NULL DEFAULT 'local',
    external_subject  VARCHAR(255) NOT NULL,
    email               VARCHAR(320) NOT NULL,
    normalized_email    VARCHAR(320) NOT NULL,
    display_name        VARCHAR(200) NOT NULL,
    password_hash       VARCHAR(255),
    status              VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    last_sign_in_at     TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_app_user_status CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED')),
    CONSTRAINT uq_app_user_provider_subject UNIQUE (identity_provider, external_subject)
);

CREATE INDEX IF NOT EXISTS idx_app_user_normalized_email ON iam.app_user(normalized_email);

-- -----------------------------------------------------------------------------
-- Roles
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iam.role (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    role_scope  VARCHAR(24) NOT NULL DEFAULT 'ORGANIZATION',
    system_role BOOLEAN NOT NULL DEFAULT TRUE,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    organization_id UUID REFERENCES org.organization(id),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_role_scope CHECK (role_scope IN ('PLATFORM', 'ORGANIZATION', 'SITE', 'BUILDING')),
    CONSTRAINT uq_role_code UNIQUE (code)
);

-- -----------------------------------------------------------------------------
-- Permissions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iam.permission (
    id          UUID PRIMARY KEY,
    code        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_permission_code UNIQUE (code)
);

-- -----------------------------------------------------------------------------
-- Role permissions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iam.role_permission (
    role_id       UUID NOT NULL REFERENCES iam.role(id),
    permission_id UUID NOT NULL REFERENCES iam.permission(id),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

-- -----------------------------------------------------------------------------
-- User organization membership
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iam.user_organization (
    id                 UUID PRIMARY KEY,
    organization_id    UUID NOT NULL REFERENCES org.organization(id),
    user_id            UUID NOT NULL REFERENCES iam.app_user(id),
    membership_status  VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    invited_by_user_id UUID REFERENCES iam.app_user(id),
    invitation_expires_at TIMESTAMP WITH TIME ZONE,
    accepted_at        TIMESTAMP WITH TIME ZONE,
    revoked_at         TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_user_org_status CHECK (membership_status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT uq_user_organization UNIQUE (organization_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_organization_user ON iam.user_organization(user_id);

-- -----------------------------------------------------------------------------
-- Role assignments (ties a membership to a role, optionally scoped to a site/building)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iam.role_assignment (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL REFERENCES org.organization(id),
    user_organization_id UUID NOT NULL REFERENCES iam.user_organization(id),
    role_id             UUID NOT NULL REFERENCES iam.role(id),
    site_id             UUID,
    building_id         UUID,
    valid_from          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_to            TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_role_assignment_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT uq_role_assignment_active UNIQUE (organization_id, user_organization_id, role_id, site_id, building_id, valid_from)
);

CREATE INDEX IF NOT EXISTS idx_role_assignment_membership ON iam.role_assignment(user_organization_id);

-- -----------------------------------------------------------------------------
-- Refresh tokens
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iam.refresh_token (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES iam.app_user(id),
    token_hash      VARCHAR(255) NOT NULL,
    issued_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at      TIMESTAMP WITH TIME ZONE,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by_token_hash VARCHAR(255),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON iam.refresh_token(user_id);
