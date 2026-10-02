package com.enerlytics.billing.domain;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "billing", name = "energy_cost",
        uniqueConstraints = @UniqueConstraint(name = "uq_energy_cost",
                columnNames = {"organization_id", "dimension_type", "dimension_id", "granularity", "bucket_start"}))
public class EnergyCostEntity {

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

    @Column(name = "tariff_id")
    private UUID tariffId;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "energy_consumed_kwh", nullable = false, precision = 24, scale = 9)
    private BigDecimal energyConsumedKwh = BigDecimal.ZERO;

    @Column(name = "energy_cost", precision = 24, scale = 9)
    private BigDecimal energyCost;

    @Column(name = "demand_charge", precision = 24, scale = 9)
    private BigDecimal demandCharge;

    @Column(name = "total_cost", precision = 24, scale = 9)
    private BigDecimal totalCost;

    @Column(name = "coverage_ratio", nullable = false, precision = 9, scale = 6)
    private BigDecimal coverageRatio = BigDecimal.ZERO;

    @Column(name = "quality_status", nullable = false, length = 16)
    private String qualityStatus = "UNAVAILABLE";

    @Column(name = "missing_rate_hours", nullable = false)
    private int missingRateHours;

    @Column(name = "calculation_run_id", nullable = false)
    private UUID calculationRunId;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected EnergyCostEntity() {
    }

    public EnergyCostEntity(UUID organizationId, DimensionType dimensionType, UUID dimensionId,
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

    public void applyMetrics(UUID tariffId, String currency, BigDecimal energyConsumedKwh,
                             BigDecimal energyCost, BigDecimal demandCharge, BigDecimal totalCost,
                             BigDecimal coverageRatio, String qualityStatus, int missingRateHours,
                             UUID calculationRunId) {
        this.tariffId = tariffId;
        this.currency = currency;
        this.energyConsumedKwh = energyConsumedKwh;
        this.energyCost = energyCost;
        this.demandCharge = demandCharge;
        this.totalCost = totalCost;
        this.coverageRatio = coverageRatio;
        this.qualityStatus = qualityStatus;
        this.missingRateHours = missingRateHours;
        this.calculationRunId = calculationRunId;
        this.computedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public DimensionType getDimensionType() { return dimensionType; }
    public UUID getDimensionId() { return dimensionId; }
    public AggregationGranularity getGranularity() { return granularity; }
    public Instant getBucketStart() { return bucketStart; }
    public Instant getBucketEnd() { return bucketEnd; }
    public UUID getTariffId() { return tariffId; }
    public String getCurrency() { return currency; }
    public BigDecimal getEnergyConsumedKwh() { return energyConsumedKwh; }
    public BigDecimal getEnergyCost() { return energyCost; }
    public BigDecimal getDemandCharge() { return demandCharge; }
    public BigDecimal getTotalCost() { return totalCost; }
    public BigDecimal getCoverageRatio() { return coverageRatio; }
    public String getQualityStatus() { return qualityStatus; }
    public int getMissingRateHours() { return missingRateHours; }
    public UUID getCalculationRunId() { return calculationRunId; }
    public Instant getComputedAt() { return computedAt; }
}
