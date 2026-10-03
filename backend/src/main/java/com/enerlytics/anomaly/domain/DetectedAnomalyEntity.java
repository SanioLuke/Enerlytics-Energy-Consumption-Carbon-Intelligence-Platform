package com.enerlytics.anomaly.domain;

import com.enerlytics.analytics.domain.DimensionType;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "anomaly", name = "detected_anomaly",
        uniqueConstraints = @UniqueConstraint(name = "uq_anomaly",
                columnNames = {"organization_id", "dimension_type", "dimension_id", "bucket_start", "method"}))
public class DetectedAnomalyEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension_type", nullable = false, length = 16)
    private DimensionType dimensionType;

    @Column(name = "dimension_id", nullable = false)
    private UUID dimensionId;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "actual_kwh", nullable = false, precision = 24, scale = 9)
    private BigDecimal actualKwh;

    @Column(name = "expected_kwh", nullable = false, precision = 24, scale = 9)
    private BigDecimal expectedKwh;

    @Column(name = "deviation_pct", nullable = false, precision = 14, scale = 6)
    private BigDecimal deviationPct;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 32)
    private AnomalyMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private AnomalySeverity severity;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "explanation", nullable = false, length = 1000)
    private String explanation;

    @Column(name = "suppressed", nullable = false)
    private boolean suppressed;

    @Column(name = "suppression_reason", length = 64)
    private String suppressionReason;

    @Column(name = "calculation_run_id", nullable = false)
    private UUID calculationRunId;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected DetectedAnomalyEntity() {
    }

    public DetectedAnomalyEntity(UUID organizationId, DimensionType dimensionType, UUID dimensionId,
                                 Instant bucketStart, AnomalyMethod method) {
        this.organizationId = organizationId;
        this.dimensionType = dimensionType;
        this.dimensionId = dimensionId;
        this.bucketStart = bucketStart;
        this.method = method;
    }

    public void applyDetection(BigDecimal actualKwh, BigDecimal expectedKwh, BigDecimal deviationPct,
                               AnomalySeverity severity, BigDecimal confidence, String explanation,
                               boolean suppressed, String suppressionReason, UUID calculationRunId) {
        this.actualKwh = actualKwh;
        this.expectedKwh = expectedKwh;
        this.deviationPct = deviationPct;
        this.severity = severity;
        this.confidence = confidence;
        this.explanation = explanation;
        this.suppressed = suppressed;
        this.suppressionReason = suppressionReason;
        this.calculationRunId = calculationRunId;
        this.detectedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public DimensionType getDimensionType() { return dimensionType; }
    public UUID getDimensionId() { return dimensionId; }
    public Instant getBucketStart() { return bucketStart; }
    public BigDecimal getActualKwh() { return actualKwh; }
    public BigDecimal getExpectedKwh() { return expectedKwh; }
    public BigDecimal getDeviationPct() { return deviationPct; }
    public AnomalyMethod getMethod() { return method; }
    public AnomalySeverity getSeverity() { return severity; }
    public BigDecimal getConfidence() { return confidence; }
    public String getExplanation() { return explanation; }
    public boolean isSuppressed() { return suppressed; }
    public String getSuppressionReason() { return suppressionReason; }
    public UUID getCalculationRunId() { return calculationRunId; }
    public Instant getDetectedAt() { return detectedAt; }
}
