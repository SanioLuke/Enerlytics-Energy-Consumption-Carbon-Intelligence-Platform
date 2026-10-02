package com.enerlytics.carbon.domain;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "carbon", name = "emission",
        uniqueConstraints = @UniqueConstraint(name = "uq_emission",
                columnNames = {"organization_id", "dimension_type", "dimension_id", "granularity", "bucket_start"}))
public class CarbonEmissionEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "granularity", nullable = false, length = 16)
    private AggregationGranularity granularity;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "bucket_end", nullable = false)
    private Instant bucketEnd;

    @Column(name = "grid_region_code", length = 100)
    private String gridRegionCode;

    @Column(name = "energy_consumed_kwh", nullable = false, precision = 24, scale = 9)
    private BigDecimal energyConsumedKwh = BigDecimal.ZERO;

    @Column(name = "carbon_intensity_gco2eq_per_kwh", precision = 20, scale = 9)
    private BigDecimal carbonIntensityGCo2EqPerKwh;

    @Column(name = "carbon_intensity_source", length = 64)
    private String carbonIntensitySource;

    @Column(name = "emissions_kgco2eq", nullable = false, precision = 24, scale = 9)
    private BigDecimal emissionsKgCo2Eq = BigDecimal.ZERO;

    @Column(name = "emissions_gco2eq", nullable = false, precision = 24, scale = 9)
    private BigDecimal emissionsGCo2Eq = BigDecimal.ZERO;

    @Column(name = "emissions_tco2eq", nullable = false, precision = 24, scale = 9)
    private BigDecimal emissionsTCo2Eq = BigDecimal.ZERO;

    @Column(name = "coverage_ratio", nullable = false, precision = 9, scale = 6)
    private BigDecimal coverageRatio = BigDecimal.ZERO;

    @Column(name = "estimated", nullable = false)
    private boolean estimated;

    @Column(name = "quality_status", nullable = false, length = 16)
    private String qualityStatus = "UNAVAILABLE";

    @Column(name = "missing_factor_hours", nullable = false)
    private int missingFactorHours;

    @Column(name = "calculation_run_id", nullable = false)
    private UUID calculationRunId;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected CarbonEmissionEntity() {
    }

    public CarbonEmissionEntity(UUID organizationId, DimensionType dimensionType, UUID dimensionId,
                                AggregationGranularity granularity, Instant bucketStart, Instant bucketEnd,
                                UUID calculationRunId) {
        this.organizationId = organizationId;
        this.dimensionType = dimensionType;
        this.dimensionId = dimensionId;
        this.granularity = granularity;
        this.bucketStart = bucketStart;
        this.bucketEnd = bucketEnd;
        this.calculationRunId = calculationRunId;
        this.computedAt = Instant.now();
    }

    public void applyMetrics(BigDecimal energyConsumedKwh, BigDecimal carbonIntensityGCo2EqPerKwh,
                             String carbonIntensitySource, BigDecimal emissionsKgCo2Eq, BigDecimal coverageRatio,
                             boolean estimated, String qualityStatus, int missingFactorHours,
                             UUID calculationRunId) {
        this.energyConsumedKwh = energyConsumedKwh;
        this.calculationRunId = calculationRunId;
        this.carbonIntensityGCo2EqPerKwh = carbonIntensityGCo2EqPerKwh;
        this.carbonIntensitySource = carbonIntensitySource;
        this.emissionsKgCo2Eq = emissionsKgCo2Eq;
        this.emissionsGCo2Eq = emissionsKgCo2Eq.multiply(BigDecimal.valueOf(1000));
        this.emissionsTCo2Eq = emissionsKgCo2Eq.divide(BigDecimal.valueOf(1000), 9, java.math.RoundingMode.HALF_EVEN);
        this.coverageRatio = coverageRatio;
        this.estimated = estimated;
        this.qualityStatus = qualityStatus;
        this.missingFactorHours = missingFactorHours;
        this.computedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public DimensionType getDimensionType() { return dimensionType; }
    public UUID getDimensionId() { return dimensionId; }
    public AggregationGranularity getGranularity() { return granularity; }
    public Instant getBucketStart() { return bucketStart; }
    public Instant getBucketEnd() { return bucketEnd; }
    public String getGridRegionCode() { return gridRegionCode; }
    public void setGridRegionCode(String gridRegionCode) { this.gridRegionCode = gridRegionCode; }
    public BigDecimal getEnergyConsumedKwh() { return energyConsumedKwh; }
    public BigDecimal getCarbonIntensityGCo2EqPerKwh() { return carbonIntensityGCo2EqPerKwh; }
    public String getCarbonIntensitySource() { return carbonIntensitySource; }
    public BigDecimal getEmissionsKgCo2Eq() { return emissionsKgCo2Eq; }
    public BigDecimal getEmissionsGCo2Eq() { return emissionsGCo2Eq; }
    public BigDecimal getEmissionsTCo2Eq() { return emissionsTCo2Eq; }
    public BigDecimal getCoverageRatio() { return coverageRatio; }
    public boolean isEstimated() { return estimated; }
    public String getQualityStatus() { return qualityStatus; }
    public int getMissingFactorHours() { return missingFactorHours; }
    public UUID getCalculationRunId() { return calculationRunId; }
    public Instant getComputedAt() { return computedAt; }
}
