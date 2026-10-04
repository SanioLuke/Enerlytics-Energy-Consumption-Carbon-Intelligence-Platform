package com.enerlytics.forecast.domain;

import com.enerlytics.common.domain.AuditedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "forecast", name = "forecast_point")
public class ForecastPointEntity extends AuditedEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "predicted_kwh", nullable = false, precision = 24, scale = 9)
    private BigDecimal predictedKwh;

    @Column(name = "lower_bound_kwh", nullable = false, precision = 24, scale = 9)
    private BigDecimal lowerBoundKwh;

    @Column(name = "upper_bound_kwh", nullable = false, precision = 24, scale = 9)
    private BigDecimal upperBoundKwh;

    @Column(name = "actual_kwh", precision = 24, scale = 9)
    private BigDecimal actualKwh;

    @Column(name = "absolute_error", precision = 24, scale = 9)
    private BigDecimal absoluteError;

    @Column(name = "pct_error", precision = 14, scale = 6)
    private BigDecimal pctError;

    @Column(name = "explanation", nullable = false, length = 1000)
    private String explanation;

    protected ForecastPointEntity() {
    }

    public ForecastPointEntity(UUID runId, UUID organizationId, Instant bucketStart,
                               BigDecimal predictedKwh, BigDecimal lowerBoundKwh,
                               BigDecimal upperBoundKwh, String explanation) {
        this.runId = runId;
        this.organizationId = organizationId;
        this.bucketStart = bucketStart;
        this.predictedKwh = predictedKwh;
        this.lowerBoundKwh = lowerBoundKwh;
        this.upperBoundKwh = upperBoundKwh;
        this.explanation = explanation;
    }

    public void applyActual(BigDecimal actualKwh, BigDecimal absoluteError, BigDecimal pctError) {
        this.actualKwh = actualKwh;
        this.absoluteError = absoluteError;
        this.pctError = pctError;
    }

    public UUID getRunId() { return runId; }
    public UUID getOrganizationId() { return organizationId; }
    public Instant getBucketStart() { return bucketStart; }
    public BigDecimal getPredictedKwh() { return predictedKwh; }
    public BigDecimal getLowerBoundKwh() { return lowerBoundKwh; }
    public BigDecimal getUpperBoundKwh() { return upperBoundKwh; }
    public BigDecimal getActualKwh() { return actualKwh; }
    public BigDecimal getAbsoluteError() { return absoluteError; }
    public BigDecimal getPctError() { return pctError; }
    public String getExplanation() { return explanation; }
}
