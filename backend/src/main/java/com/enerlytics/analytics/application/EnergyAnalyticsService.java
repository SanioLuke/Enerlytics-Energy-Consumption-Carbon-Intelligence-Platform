package com.enerlytics.analytics.application;

import com.enerlytics.analytics.api.dto.EnergyAggregateResponse;
import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.facility.infrastructure.persistence.ZoneRepository;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read path for precomputed energy aggregates. Dashboards and reports query
 * {@code analytics.energy_aggregate} directly — never meter_reading.
 */
@Service
public class EnergyAnalyticsService {

    private static final int MAX_BUCKETS = 10_000;

    private final EnergyAggregateRepository aggregateRepository;
    private final MeterRepository meterRepository;
    private final SiteRepository siteRepository;
    private final BuildingRepository buildingRepository;
    private final ZoneRepository zoneRepository;

    public EnergyAnalyticsService(EnergyAggregateRepository aggregateRepository,
                                  MeterRepository meterRepository,
                                  SiteRepository siteRepository,
                                  BuildingRepository buildingRepository,
                                  ZoneRepository zoneRepository) {
        this.aggregateRepository = aggregateRepository;
        this.meterRepository = meterRepository;
        this.siteRepository = siteRepository;
        this.buildingRepository = buildingRepository;
        this.zoneRepository = zoneRepository;
    }

    @Transactional(readOnly = true)
    public List<EnergyAggregateResponse> series(UUID organizationId, DimensionType dimension, UUID dimensionId,
                                                AggregationGranularity granularity, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be earlier than 'to'");
        }
        UUID resolvedId = resolveDimension(organizationId, dimension, dimensionId);
        List<EnergyAggregateEntity> rows = aggregateRepository
                .findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
                        organizationId, dimension, resolvedId, granularity, from, to.minusNanos(1));
        if (rows.size() > MAX_BUCKETS) {
            throw new IllegalArgumentException("Requested range exceeds " + MAX_BUCKETS + " buckets");
        }
        return rows.stream().map(this::toResponse).toList();
    }

    private UUID resolveDimension(UUID organizationId, DimensionType dimension, UUID dimensionId) {
        return switch (dimension) {
            case ORGANIZATION -> {
                if (dimensionId != null && !dimensionId.equals(organizationId)) {
                    throw new EntityNotFoundException("Organization dimension not found");
                }
                yield organizationId;
            }
            case METER -> {
                requireId(dimension, dimensionId);
                yield meterRepository.findByIdAndOrganization_Id(dimensionId, organizationId)
                        .orElseThrow(() -> new EntityNotFoundException("Meter not found")).getId();
            }
            case SITE -> {
                requireId(dimension, dimensionId);
                yield siteRepository.findByIdAndOrganizationIdAndActiveTrue(dimensionId, organizationId)
                        .orElseThrow(() -> new EntityNotFoundException("Site not found")).getId();
            }
            case BUILDING -> {
                requireId(dimension, dimensionId);
                yield buildingRepository.findByIdAndOrganizationIdAndActiveTrue(dimensionId, organizationId)
                        .orElseThrow(() -> new EntityNotFoundException("Building not found")).getId();
            }
            case ZONE -> {
                requireId(dimension, dimensionId);
                yield zoneRepository.findByIdAndOrganizationIdAndActiveTrue(dimensionId, organizationId)
                        .orElseThrow(() -> new EntityNotFoundException("Zone not found")).getId();
            }
        };
    }

    private void requireId(DimensionType dimension, UUID dimensionId) {
        if (dimensionId == null) {
            throw new IllegalArgumentException("dimensionId is required for " + dimension);
        }
    }

    private EnergyAggregateResponse toResponse(EnergyAggregateEntity e) {
        return new EnergyAggregateResponse(
                e.getDimensionType(), e.getDimensionId(), e.getGranularity(),
                e.getBucketStart(), e.getBucketEnd(),
                e.getEnergyConsumedKwh(), e.getAveragePowerKw(), e.getPeakPowerKw(), e.getMinimumPowerKw(),
                e.getAveragePowerFactor(), e.getReadingCount(), e.getEstimatedReadingCount(),
                e.getDataCompletenessPct(), e.getComputedAt());
    }
}
