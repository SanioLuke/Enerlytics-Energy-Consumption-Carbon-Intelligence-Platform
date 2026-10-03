package com.enerlytics.anomaly.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.anomaly.api.dto.AnomalyResponse;
import com.enerlytics.anomaly.config.AnomalyDetectionProperties;
import com.enerlytics.anomaly.detection.AnomalyCandidate;
import com.enerlytics.anomaly.detection.AnomalyDetector;
import com.enerlytics.anomaly.detection.HourlyPoint;
import com.enerlytics.anomaly.domain.DetectedAnomalyEntity;
import com.enerlytics.anomaly.domain.MaintenanceWindowEntity;
import com.enerlytics.anomaly.infrastructure.persistence.DetectedAnomalyRepository;
import com.enerlytics.anomaly.infrastructure.persistence.MaintenanceWindowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AnomalyDetectionService {

    private final List<AnomalyDetector> detectors;
    private final AnomalyDetectionProperties properties;
    private final EnergyAggregateRepository energyRepository;
    private final DetectedAnomalyRepository anomalyRepository;
    private final MaintenanceWindowRepository maintenanceRepository;

    public AnomalyDetectionService(List<AnomalyDetector> detectors,
                                   AnomalyDetectionProperties properties,
                                   EnergyAggregateRepository energyRepository,
                                   DetectedAnomalyRepository anomalyRepository,
                                   MaintenanceWindowRepository maintenanceRepository) {
        this.detectors = detectors.stream().sorted(Comparator.comparing(d -> d.method().name())).toList();
        this.properties = properties;
        this.energyRepository = energyRepository;
        this.anomalyRepository = anomalyRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    /**
     * Recalculates canonical anomaly records for a dimension and half-open range.
     * Incomplete telemetry is excluded before detectors run. Startup points are
     * ignored, maintenance candidates are retained as suppressed audit records,
     * and public history excludes all suppressed records.
     */
    @Transactional
    public List<AnomalyResponse> detect(UUID organizationId, DimensionType dimensionType,
                                        UUID dimensionId, Instant from, Instant to) {
        validateRange(from, to);
        Instant baselineFrom = from.minus(Duration.ofDays(properties.getBaselineLookbackDays()));
        List<HourlyPoint> series = energyRepository
                .findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
                        organizationId, dimensionType, dimensionId, AggregationGranularity.HOUR,
                        baselineFrom, to)
                .stream()
                .filter(e -> e.getBucketStart().isBefore(to))
                .filter(this::hasUsableTelemetry)
                .map(e -> new HourlyPoint(e.getBucketStart(), e.getEnergyConsumedKwh(),
                        e.getDataCompletenessPct()))
                .toList();

        List<DetectedAnomalyEntity> oldRows = anomalyRepository
                .findByOrganizationIdAndDimensionTypeAndDimensionIdAndBucketStartBetweenOrderByBucketStartDesc(
                        organizationId, dimensionType, dimensionId, from, to)
                .stream().filter(e -> e.getBucketStart().isBefore(to)).toList();
        anomalyRepository.deleteAll(oldRows);
        anomalyRepository.flush();

        Instant firstObserved = energyRepository
                .findFirstByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityOrderByBucketStart(
                        organizationId, dimensionType, dimensionId, AggregationGranularity.HOUR)
                .map(EnergyAggregateEntity::getBucketStart)
                .orElse(null);
        if (firstObserved == null) {
            return List.of();
        }
        Instant startupEndsAt = firstObserved.plus(Duration.ofHours(properties.getStartupHours()));
        List<MaintenanceWindowEntity> maintenance = maintenanceRepository.findApplicable(
                organizationId, dimensionType, dimensionId, from, to);
        UUID runId = UUID.randomUUID();
        List<DetectedAnomalyEntity> saved = new ArrayList<>();

        for (AnomalyDetector detector : detectors) {
            for (AnomalyCandidate candidate : detector.detect(series, properties)) {
                if (candidate.bucketStart().isBefore(from) || !candidate.bucketStart().isBefore(to)) {
                    continue;
                }
                if (candidate.bucketStart().isBefore(startupEndsAt)) {
                    continue;
                }
                MaintenanceWindowEntity window = maintenance.stream()
                        .filter(w -> w.covers(candidate.bucketStart()))
                        .findFirst().orElse(null);
                boolean suppressed = window != null;
                String suppressionReason = suppressed ? "MAINTENANCE_PERIOD" : null;

                DetectedAnomalyEntity entity = new DetectedAnomalyEntity(
                        organizationId, dimensionType, dimensionId, candidate.bucketStart(), candidate.method());
                entity.applyDetection(candidate.actualKwh(), candidate.expectedKwh(),
                        candidate.deviationPct(), candidate.severity(), candidate.confidence(),
                        candidate.explanation(), suppressed, suppressionReason, runId);
                saved.add(anomalyRepository.save(entity));
            }
        }
        return saved.stream().filter(e -> !e.isSuppressed()).map(this::toResponse)
                .sorted(Comparator.comparing(AnomalyResponse::timestamp).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public List<AnomalyResponse> history(UUID organizationId, DimensionType dimensionType,
                                         UUID dimensionId, Instant from, Instant to) {
        validateRange(from, to);
        return anomalyRepository
                .findByOrganizationIdAndDimensionTypeAndDimensionIdAndBucketStartBetweenAndSuppressedFalseOrderByBucketStartDesc(
                        organizationId, dimensionType, dimensionId, from, to)
                .stream().filter(e -> e.getBucketStart().isBefore(to)).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AnomalyResponse> organizationHistory(UUID organizationId, Instant from, Instant to) {
        validateRange(from, to);
        return anomalyRepository
                .findByOrganizationIdAndBucketStartBetweenAndSuppressedFalseOrderByBucketStartDesc(
                        organizationId, from, to)
                .stream().filter(e -> e.getBucketStart().isBefore(to)).map(this::toResponse).toList();
    }

    private boolean hasUsableTelemetry(EnergyAggregateEntity row) {
        return row.getEnergyConsumedKwh() != null
                && row.getDataCompletenessPct() != null
                && row.getDataCompletenessPct().compareTo(properties.getMinCompletenessPct()) >= 0;
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
        if (Duration.between(from, to).toDays() > 366) {
            throw new IllegalArgumentException("Requested range must not exceed 366 days");
        }
    }

    private AnomalyResponse toResponse(DetectedAnomalyEntity e) {
        return new AnomalyResponse(e.getId(), e.getDimensionType(), e.getDimensionId(),
                e.getBucketStart(), e.getActualKwh(), e.getExpectedKwh(), e.getDeviationPct(),
                e.getMethod(), e.getConfidence(), e.getSeverity(), e.getExplanation(), e.getDetectedAt());
    }
}
