package com.enerlytics.alert.domain;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.common.domain.AuditedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(schema = "alerting", name = "alert_instance")
public class AlertInstanceEntity extends AuditedEntity {

    @Column(name = "organization_id", nullable = false)
    private java.util.UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRuleEntity rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    private DimensionType scopeType;

    @Column(name = "scope_id", nullable = false)
    private java.util.UUID scopeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 32)
    private AlertType alertType;

    @Column(name = "metric", nullable = false, length = 64)
    private String metric;

    @Column(name = "observed_value", nullable = false, precision = 24, scale = 9)
    private BigDecimal observedValue;

    @Column(name = "threshold", nullable = false, precision = 24, scale = 9)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 255)
    private String acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "context_payload", nullable = false)
    private String contextPayload;

    @Column(name = "deduplication_key", nullable = false, length = 255)
    private String deduplicationKey;

    protected AlertInstanceEntity() {
    }

    public AlertInstanceEntity(java.util.UUID organizationId, AlertRuleEntity rule,
                               DimensionType scopeType, java.util.UUID scopeId, AlertType alertType,
                               String metric, BigDecimal observedValue, BigDecimal threshold,
                               AlertSeverity severity, Instant triggeredAt, String contextPayload,
                               String deduplicationKey) {
        this.organizationId = organizationId;
        this.rule = rule;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.alertType = alertType;
        this.metric = metric;
        this.observedValue = observedValue;
        this.threshold = threshold;
        this.severity = severity;
        this.triggeredAt = triggeredAt;
        this.contextPayload = contextPayload;
        this.deduplicationKey = deduplicationKey;
    }

    public void acknowledge(String user, Instant at) {
        if (this.status == AlertStatus.RESOLVED) {
            throw new IllegalStateException("Cannot acknowledge a resolved alert");
        }
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedAt = at;
        this.acknowledgedBy = user;
    }

    public void resolve(String user, Instant at) {
        if (this.status == AlertStatus.RESOLVED) {
            throw new IllegalStateException("Alert is already resolved");
        }
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = at;
        this.resolvedBy = user;
    }

    public java.util.UUID getOrganizationId() { return organizationId; }
    public AlertRuleEntity getRule() { return rule; }
    public DimensionType getScopeType() { return scopeType; }
    public java.util.UUID getScopeId() { return scopeId; }
    public AlertType getAlertType() { return alertType; }
    public String getMetric() { return metric; }
    public BigDecimal getObservedValue() { return observedValue; }
    public BigDecimal getThreshold() { return threshold; }
    public AlertSeverity getSeverity() { return severity; }
    public AlertStatus getStatus() { return status; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public String getContextPayload() { return contextPayload; }
    public String getDeduplicationKey() { return deduplicationKey; }
}
