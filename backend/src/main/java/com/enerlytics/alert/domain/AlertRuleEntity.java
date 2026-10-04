package com.enerlytics.alert.domain;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.common.domain.AuditedEntity;
import com.enerlytics.organization.domain.OrganizationEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(schema = "alerting", name = "alert_rule")
public class AlertRuleEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 32)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    private DimensionType scopeType;

    @Column(name = "scope_id", nullable = false)
    private java.util.UUID scopeId;

    @Column(name = "metric", nullable = false, length = 64)
    private String metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator", nullable = false, length = 8)
    private ComparisonOperator comparisonOperator;

    @Column(name = "threshold", nullable = false, precision = 24, scale = 9)
    private BigDecimal threshold;

    @Column(name = "evaluation_window_seconds", nullable = false)
    private long evaluationWindowSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private AlertSeverity severity;

    @Column(name = "cooldown_seconds", nullable = false)
    private long cooldownSeconds;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected AlertRuleEntity() {
    }

    public AlertRuleEntity(OrganizationEntity organization, String name, AlertType alertType,
                           DimensionType scopeType, java.util.UUID scopeId, String metric,
                           ComparisonOperator operator, BigDecimal threshold,
                           long evaluationWindowSeconds, AlertSeverity severity,
                           long cooldownSeconds) {
        this.organization = organization;
        this.name = name;
        this.alertType = alertType;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.metric = metric;
        this.comparisonOperator = operator;
        this.threshold = threshold;
        this.evaluationWindowSeconds = evaluationWindowSeconds;
        this.severity = severity;
        this.cooldownSeconds = cooldownSeconds;
    }

    public boolean isApplicableTo(DimensionType dimensionType, java.util.UUID dimensionId) {
        return scopeType == dimensionType && scopeId.equals(dimensionId);
    }

    public void setName(String name) { this.name = name; }
    public void setAlertType(AlertType alertType) { this.alertType = alertType; }
    public void setScopeType(DimensionType scopeType) { this.scopeType = scopeType; }
    public void setScopeId(java.util.UUID scopeId) { this.scopeId = scopeId; }
    public void setMetric(String metric) { this.metric = metric; }
    public void setComparisonOperator(ComparisonOperator comparisonOperator) { this.comparisonOperator = comparisonOperator; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }
    public void setEvaluationWindowSeconds(long evaluationWindowSeconds) { this.evaluationWindowSeconds = evaluationWindowSeconds; }
    public void setSeverity(AlertSeverity severity) { this.severity = severity; }
    public void setCooldownSeconds(long cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public OrganizationEntity getOrganization() { return organization; }
    public String getName() { return name; }
    public AlertType getAlertType() { return alertType; }
    public DimensionType getScopeType() { return scopeType; }
    public java.util.UUID getScopeId() { return scopeId; }
    public String getMetric() { return metric; }
    public ComparisonOperator getComparisonOperator() { return comparisonOperator; }
    public BigDecimal getThreshold() { return threshold; }
    public long getEvaluationWindowSeconds() { return evaluationWindowSeconds; }
    public AlertSeverity getSeverity() { return severity; }
    public long getCooldownSeconds() { return cooldownSeconds; }
    public boolean isEnabled() { return enabled; }
}
