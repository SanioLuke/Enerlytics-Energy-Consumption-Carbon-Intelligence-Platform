package com.enerlytics.forecast.domain;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.common.domain.AuditedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "forecast", name = "forecast_run")
public class ForecastRunEntity extends AuditedEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension_type", nullable = false, length = 16)
    private DimensionType dimensionType;

    @Column(name = "dimension_id", nullable = false)
    private UUID dimensionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "horizon", nullable = false, length = 16)
    private ForecastHorizon horizon;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 32)
    private ForecastMethod method;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "history_from", nullable = false)
    private Instant historyFrom;

    @Column(name = "history_to", nullable = false)
    private Instant historyTo;

    @Column(name = "mae_kwh", precision = 24, scale = 9)
    private BigDecimal maeKwh;

    @Column(name = "mape_pct", precision = 14, scale = 6)
    private BigDecimal mapePct;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(name = "evaluated_points", nullable = false)
    private int evaluatedPoints;

    protected ForecastRunEntity() {
    }

    public ForecastRunEntity(UUID organizationId, DimensionType dimensionType, UUID dimensionId,
                             ForecastHorizon horizon, ForecastMethod method, Instant generatedAt,
                             Instant historyFrom, Instant historyTo) {
        this.organizationId = organizationId;
        this.dimensionType = dimensionType;
        this.dimensionId = dimensionId;
        this.horizon = horizon;
        this.method = method;
        this.generatedAt = generatedAt;
        this.historyFrom = historyFrom;
        this.historyTo = historyTo;
    }

    public void applyEvaluation(BigDecimal maeKwh, BigDecimal mapePct, int evaluatedPoints,
                                Instant evaluatedAt) {
        this.maeKwh = maeKwh;
        this.mapePct = mapePct;
        this.evaluatedPoints = evaluatedPoints;
        this.evaluatedAt = evaluatedAt;
    }

    public UUID getOrganizationId() { return organizationId; }
    public DimensionType getDimensionType() { return dimensionType; }
    public UUID getDimensionId() { return dimensionId; }
    public ForecastHorizon getHorizon() { return horizon; }
    public ForecastMethod getMethod() { return method; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getHistoryFrom() { return historyFrom; }
    public Instant getHistoryTo() { return historyTo; }
    public BigDecimal getMaeKwh() { return maeKwh; }
    public BigDecimal getMapePct() { return mapePct; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public int getEvaluatedPoints() { return evaluatedPoints; }
}
