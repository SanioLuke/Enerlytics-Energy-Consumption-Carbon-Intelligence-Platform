package com.enerlytics.alert.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.carbon.domain.CarbonEmissionEntity;
import com.enerlytics.carbon.infrastructure.persistence.CarbonEmissionRepository;
import com.enerlytics.alert.infrastructure.persistence.AlertInstanceRepository;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Resolves the latest metric value for a given scope used by alert rules. */
@Component
public class AlertMetricsResolver {

    private final EnergyAggregateRepository energyAggregateRepository;
    private final CarbonEmissionRepository carbonEmissionRepository;
    private final MeterRepository meterRepository;
    private final AlertInstanceRepository alertInstanceRepository;

    public AlertMetricsResolver(EnergyAggregateRepository energyAggregateRepository,
                                CarbonEmissionRepository carbonEmissionRepository,
                                MeterRepository meterRepository,
                                AlertInstanceRepository alertInstanceRepository) {
        this.energyAggregateRepository = energyAggregateRepository;
        this.carbonEmissionRepository = carbonEmissionRepository;
        this.meterRepository = meterRepository;
        this.alertInstanceRepository = alertInstanceRepository;
    }

    public Optional<MetricReading> resolve(UUID organizationId, DimensionType scopeType,
                                           UUID scopeId, String metric, Instant evaluationTime) {
        Instant bucketStart = AggregationGranularity.HOUR.bucketStart(evaluationTime);
        return switch (metric.toUpperCase()) {
            case "ENERGY_CONSUMED_KWH" -> hourly(scopeType, scopeId, organizationId, bucketStart)
                    .map(e -> new MetricReading(e.getBucketStart(), e.getEnergyConsumedKwh(), metric));
            case "PEAK_POWER_KW" -> hourly(scopeType, scopeId, organizationId, bucketStart)
                    .map(e -> new MetricReading(e.getBucketStart(), e.getPeakPowerKw(), metric));
            case "AVERAGE_POWER_KW" -> hourly(scopeType, scopeId, organizationId, bucketStart)
                    .map(e -> new MetricReading(e.getBucketStart(), e.getAveragePowerKw(), metric));
            case "CARBON_INTENSITY_GCO2EQ_PER_KWH" -> carbonEmissionRepository
                    .findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                            organizationId, scopeType, scopeId, AggregationGranularity.HOUR, bucketStart)
                    .map(e -> new MetricReading(e.getBucketStart(), e.getCarbonIntensityGCo2EqPerKwh(), metric));
            case "LAST_SEEN_SECONDS" -> lastSeenSeconds(scopeType, scopeId)
                    .map(v -> new MetricReading(evaluationTime, v, metric));
            case "ANOMALY_COUNT" -> anomalyCount(scopeType, scopeId, organizationId, bucketStart)
                    .map(v -> new MetricReading(bucketStart, v, metric));
            case "TARGET_EXCEEDED_KWH" -> hourly(scopeType, scopeId, organizationId, bucketStart)
                    .map(e -> new MetricReading(e.getBucketStart(), e.getEnergyConsumedKwh(), metric));
            default -> Optional.empty();
        };
    }

    private Optional<EnergyAggregateEntity> hourly(DimensionType dimensionType, UUID dimensionId,
                                                   UUID organizationId, Instant bucketStart) {
        return energyAggregateRepository.findByDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                dimensionType, dimensionId, AggregationGranularity.HOUR, bucketStart);
    }

    private Optional<BigDecimal> lastSeenSeconds(DimensionType scopeType, UUID scopeId) {
        if (scopeType != DimensionType.METER) {
            return Optional.empty();
        }
        return meterRepository.findById(scopeId)
                .map(MeterEntity::getLastSeenAt)
                .map(lastSeen -> BigDecimal.valueOf(
                        java.time.Duration.between(lastSeen, Instant.now()).getSeconds()));
    }

    private Optional<BigDecimal> anomalyCount(DimensionType scopeType, UUID scopeId,
                                             UUID organizationId, Instant bucketStart) {
        long count = alertInstanceRepository.countByOrganizationIdAndScopeTypeAndScopeIdAndTriggeredAtBetween(
                organizationId, scopeType, scopeId, bucketStart, bucketStart.plusSeconds(3600));
        return Optional.of(BigDecimal.valueOf(count));
    }

    public record MetricReading(Instant bucketStart, BigDecimal value, String metric) {
    }
}
