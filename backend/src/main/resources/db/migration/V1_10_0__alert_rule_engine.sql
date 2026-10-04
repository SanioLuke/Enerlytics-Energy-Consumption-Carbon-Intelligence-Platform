CREATE SCHEMA IF NOT EXISTS alerting;

CREATE TABLE IF NOT EXISTS alerting.alert_rule (
    id                 UUID PRIMARY KEY,
    organization_id    UUID NOT NULL REFERENCES org.organization(id),
    name               VARCHAR(200) NOT NULL,
    alert_type         VARCHAR(32) NOT NULL,
    scope_type         VARCHAR(16) NOT NULL,
    scope_id           UUID NOT NULL,
    metric             VARCHAR(64) NOT NULL,
    comparison_operator VARCHAR(8) NOT NULL,
    threshold          DECIMAL(24,9) NOT NULL,
    evaluation_window_seconds BIGINT NOT NULL,
    severity           VARCHAR(16) NOT NULL,
    cooldown_seconds   BIGINT NOT NULL,
    enabled            BOOLEAN NOT NULL DEFAULT true,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_alert_type CHECK (alert_type IN
        ('HIGH_CONSUMPTION','HIGH_DEMAND','CARBON_INTENSITY_HIGH','METER_OFFLINE','ABNORMAL_USAGE','TARGET_EXCEEDED')),
    CONSTRAINT chk_alert_scope CHECK (scope_type IN ('ORGANIZATION','SITE','BUILDING','METER')),
    CONSTRAINT chk_alert_operator CHECK (comparison_operator IN ('GT','GTE','LT','LTE','EQ','NE')),
    CONSTRAINT chk_alert_rule_severity CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    CONSTRAINT chk_evaluation_window CHECK (evaluation_window_seconds > 0),
    CONSTRAINT chk_cooldown CHECK (cooldown_seconds >= 0)
);

CREATE INDEX idx_alert_rule_eval ON alerting.alert_rule
    (organization_id, scope_type, scope_id, metric, enabled);

CREATE TABLE IF NOT EXISTS alerting.alert_instance (
    id                 UUID PRIMARY KEY,
    organization_id    UUID NOT NULL REFERENCES org.organization(id),
    rule_id            UUID NOT NULL REFERENCES alerting.alert_rule(id),
    scope_type         VARCHAR(16) NOT NULL,
    scope_id           UUID NOT NULL,
    alert_type         VARCHAR(32) NOT NULL,
    metric             VARCHAR(64) NOT NULL,
    observed_value     DECIMAL(24,9) NOT NULL,
    threshold          DECIMAL(24,9) NOT NULL,
    severity           VARCHAR(16) NOT NULL,
    status             VARCHAR(16) NOT NULL,
    triggered_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_at    TIMESTAMP WITH TIME ZONE,
    acknowledged_by    VARCHAR(255),
    resolved_at        TIMESTAMP WITH TIME ZONE,
    resolved_by        VARCHAR(255),
    context_payload    TEXT NOT NULL,
    deduplication_key  VARCHAR(255) NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_alert_status CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED')),
    CONSTRAINT chk_instance_scope CHECK (scope_type IN ('ORGANIZATION','SITE','BUILDING','METER')),
    CONSTRAINT chk_instance_severity CHECK (severity IN ('INFO','WARNING','CRITICAL'))
);

CREATE INDEX idx_alert_history ON alerting.alert_instance
    (organization_id, triggered_at DESC);
CREATE INDEX idx_alert_active_dedup ON alerting.alert_instance
    (organization_id, rule_id, scope_id, status, triggered_at DESC);
