package com.enerlytics.analytics.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "analytics", name = "energy_aggregate",
        uniqueConstraints = @UniqueConstraint(name = "uq_energy_aggregate_bucket",
                columnNames = {"dimension_type", "dimension_id", "granularity", "bucket_start"}))
public class EnergyAggregateEntity {

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

    @Column(name = "energy_consumed_kwh", nullable = false, precision = 18, scale = 9)
    private BigDecimal energyConsumedKwh = BigDecimal.ZERO;

    @Column(name = "average_power_kw", precision = 12, scale = 6)
    private BigDecimal averagePowerKw;

    @Column(name = "peak_power_kw", precision = 12, scale = 6)
    private BigDecimal peakPowerKw;

    @Column(name = "minimum_power_kw", precision = 12, scale = 6)
    private BigDecimal minimumPowerKw;

    @Column(name = "average_power_factor", precision = 5, scale = 4)
    private BigDecimal averagePowerFactor;

    @Column(name = "reading_count", nullable = false)
    private Long readingCount = 0L;

    @Column(name = "estimated_reading_count", nullable = false)
    private Long estimatedReadingCount = 0L;

    @Column(name = "data_completeness_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal dataCompletenessPct = BigDecimal.ZERO;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected EnergyAggregateEntity() {
    }

    public EnergyAggregateEntity(UUID organizationId, DimensionType dimensionType, UUID dimensionId,
                                 AggregationGranularity granularity, Instant bucketStart, Instant bucketEnd) {
        this.organizationId = organizationId;
        this.dimensionType = dimensionType;
        this.dimensionId = dimensionId;
        this.granularity = granularity;
        this.bucketStart = bucketStart;
        this.bucketEnd = bucketEnd;
    }

    public void applyMetrics(BigDecimal energyConsumedKwh, BigDecimal averagePowerKw, BigDecimal peakPowerKw,
                             BigDecimal minimumPowerKw, BigDecimal averagePowerFactor, long readingCount,
                             long estimatedReadingCount, BigDecimal dataCompletenessPct, Instant computedAt) {
        this.energyConsumedKwh = energyConsumedKwh;
        this.averagePowerKw = averagePowerKw;
        this.peakPowerKw = peakPowerKw;
        this.minimumPowerKw = minimumPowerKw;
        this.averagePowerFactor = averagePowerFactor;
        this.readingCount = readingCount;
        this.estimatedReadingCount = estimatedReadingCount;
        this.dataCompletenessPct = dataCompletenessPct;
        this.computedAt = computedAt;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public DimensionType getDimensionType() { return dimensionType; }
    public UUID getDimensionId() { return dimensionId; }
    public AggregationGranularity getGranularity() { return granularity; }
    public Instant getBucketStart() { return bucketStart; }
    public Instant getBucketEnd() { return bucketEnd; }
    public BigDecimal getEnergyConsumedKwh() { return energyConsumedKwh; }
    public BigDecimal getAveragePowerKw() { return averagePowerKw; }
    public BigDecimal getPeakPowerKw() { return peakPowerKw; }
    public BigDecimal getMinimumPowerKw() { return minimumPowerKw; }
    public BigDecimal getAveragePowerFactor() { return averagePowerFactor; }
    public Long getReadingCount() { return readingCount; }
    public Long getEstimatedReadingCount() { return estimatedReadingCount; }
    public BigDecimal getDataCompletenessPct() { return dataCompletenessPct; }
    public Instant getComputedAt() { return computedAt; }
}
