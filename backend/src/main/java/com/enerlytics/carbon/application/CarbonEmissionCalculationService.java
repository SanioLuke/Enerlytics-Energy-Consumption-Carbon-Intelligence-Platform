package com.enerlytics.carbon.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.carbon.domain.CarbonEmissionEntity;
import com.enerlytics.carbon.domain.CarbonIntensityObservationEntity;
import com.enerlytics.carbon.infrastructure.persistence.CarbonEmissionRepository;
import com.enerlytics.carbon.infrastructure.persistence.CarbonIntensityObservationRepository;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.facility.infrastructure.persistence.ZoneRepository;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Location-based Scope 2 carbon emission calculation.
 *
 * <p>Computes emissions bottom-up from meter-level hourly energy aggregates and
 * the site's grid-region carbon intensity observations, then rolls the results up
 * to buildings, zones, sites, and the organization for requested granularities.
 * Missing or stale factors are disclosed; zero is never substituted.</p>
 */
@Service
public class CarbonEmissionCalculationService {

    private static final Logger log = LoggerFactory.getLogger(CarbonEmissionCalculationService.class);

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final MathContext INTERMEDIATE = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final Duration STALENESS_THRESHOLD = Duration.ofHours(24);

    private final CarbonEmissionRepository emissionRepository;
    private final CarbonIntensityObservationRepository observationRepository;
    private final EnergyAggregateRepository energyAggregateRepository;
    private final MeterRepository meterRepository;
    private final SiteRepository siteRepository;
    private final BuildingRepository buildingRepository;
    private final ZoneRepository zoneRepository;

    public CarbonEmissionCalculationService(CarbonEmissionRepository emissionRepository,
                                            CarbonIntensityObservationRepository observationRepository,
                                            EnergyAggregateRepository energyAggregateRepository,
                                            MeterRepository meterRepository,
                                            SiteRepository siteRepository,
                                            BuildingRepository buildingRepository,
                                            ZoneRepository zoneRepository) {
        this.emissionRepository = emissionRepository;
        this.observationRepository = observationRepository;
        this.energyAggregateRepository = energyAggregateRepository;
        this.meterRepository = meterRepository;
        this.siteRepository = siteRepository;
        this.buildingRepository = buildingRepository;
        this.zoneRepository = zoneRepository;
    }

    /**
     * Materializes emissions for the requested scope and time range.
     *
     * @return persisted emission rows ordered by bucket start
     */
    @Transactional
    public List<CarbonEmissionEntity> calculateEmissions(UUID organizationId, DimensionType dimension,
                                                           UUID dimensionId, AggregationGranularity granularity,
                                                           Instant from, Instant to) {
        if (granularity == AggregationGranularity.QUARTER_HOUR) {
            throw new IllegalArgumentException("Carbon emissions support HOUR, DAY, and MONTH granularities");
        }
        validateDimension(organizationId, dimension, dimensionId);
        UUID calculationRunId = UUID.randomUUID();

        List<ScopeMeter> meters = resolveMeters(organizationId, dimension, dimensionId);

        Map<BucketKey, BucketAccumulator> accumulators = new LinkedHashMap<>();

        for (ScopeMeter scopeMeter : meters) {
            List<EnergyAggregateEntity> energy = energyAggregateRepository
                    .findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
                            organizationId, DimensionType.METER, scopeMeter.meter.getId(),
                            AggregationGranularity.HOUR, from, to);

            for (EnergyAggregateEntity hour : energy) {
                CarbonIntensityObservationEntity factor = resolveFactor(
                        scopeMeter.region, hour.getBucketStart());
                BucketKey key = new BucketKey(dimension, dimensionId, granularity,
                        enclosingBucketStart(granularity, hour.getBucketStart()),
                        enclosingBucketEnd(granularity, hour.getBucketStart()));
                accumulate(accumulators, key, hour, factor, scopeMeter.region);
            }
        }

        List<CarbonEmissionEntity> results = new ArrayList<>();
        for (BucketKey key : accumulators.keySet()) {
            results.add(saveBucket(organizationId, key, accumulators.get(key), calculationRunId));
        }
        return results.stream().sorted(Comparator.comparing(CarbonEmissionEntity::getBucketStart)).toList();
    }

    private void validateDimension(UUID orgId, DimensionType dimension, UUID dimensionId) {
        switch (dimension) {
            case ORGANIZATION -> {
                if (!orgId.equals(dimensionId)) {
                    throw new IllegalArgumentException("Organization dimension must match the organization id");
                }
            }
            case SITE -> siteRepository.findByIdAndOrganizationIdAndActiveTrue(dimensionId, orgId)
                    .orElseThrow(() -> new EntityNotFoundException("Site not found"));
            case BUILDING -> buildingRepository.findByIdAndOrganizationIdAndActiveTrue(dimensionId, orgId)
                    .orElseThrow(() -> new EntityNotFoundException("Building not found"));
            case ZONE -> zoneRepository.findByIdAndOrganizationIdAndActiveTrue(dimensionId, orgId)
                    .orElseThrow(() -> new EntityNotFoundException("Zone not found"));
            case METER -> meterRepository.findByIdAndOrganization_Id(dimensionId, orgId)
                    .orElseThrow(() -> new EntityNotFoundException("Meter not found"));
        }
    }

    private List<ScopeMeter> resolveMeters(UUID orgId, DimensionType dimension, UUID dimensionId) {
        List<MeterEntity> meters = switch (dimension) {
            case ORGANIZATION -> meterRepository.findByOrganization_IdAndStatus(orgId, MeterStatus.ACTIVE);
            case SITE -> meterRepository.findByOrganization_IdAndSite_IdAndStatus(orgId, dimensionId, MeterStatus.ACTIVE);
            case BUILDING -> meterRepository.findByOrganization_IdAndBuilding_IdAndStatus(orgId, dimensionId, MeterStatus.ACTIVE);
            case ZONE -> meterRepository.findByOrganization_IdAndZone_IdAndStatus(orgId, dimensionId, MeterStatus.ACTIVE);
            case METER -> meterRepository.findByIdAndOrganization_Id(dimensionId, orgId).map(List::of)
                    .orElseThrow(() -> new EntityNotFoundException("Meter not found"));
        };

        return meters.stream().map(meter -> {
            SiteEntity site = meter.getSite();
            String region = site.getGridRegionCode();
            if (region == null || region.isBlank()) {
                region = site.getCountry();
            }
            if (region == null || region.isBlank()) {
                region = "UNKNOWN";
            }
            return new ScopeMeter(meter, region);
        }).toList();
    }

    private CarbonIntensityObservationEntity resolveFactor(String region, Instant hourStart) {
        Instant staleThreshold = hourStart.minus(STALENESS_THRESHOLD);
        return observationRepository.findLatestValidByZone(region, hourStart, staleThreshold,
                        PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
    }

    private void accumulate(Map<BucketKey, BucketAccumulator> accumulators, BucketKey key,
                            EnergyAggregateEntity energy, CarbonIntensityObservationEntity factor, String region) {
        BucketAccumulator acc = accumulators.computeIfAbsent(key, k -> new BucketAccumulator());
        acc.add(energy, factor, region);
    }

    private CarbonEmissionEntity saveBucket(UUID orgId, BucketKey key, BucketAccumulator acc, UUID calculationRunId) {
        CarbonEmissionEntity entity = emissionRepository
                .findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                        orgId, key.dimensionType, key.dimensionId, key.granularity, key.bucketStart)
                .orElseGet(() -> new CarbonEmissionEntity(orgId, key.dimensionType, key.dimensionId,
                        key.granularity, key.bucketStart, key.bucketEnd, calculationRunId));
        entity.setGridRegionCode(acc.gridRegionCode());
        entity.applyMetrics(acc.energy, acc.weightedIntensity(), acc.source, acc.emissions, acc.coverage(),
                acc.estimated, acc.qualityStatus(), acc.missingFactorHours, calculationRunId);
        return emissionRepository.save(entity);
    }

    private Instant enclosingBucketStart(AggregationGranularity granularity, Instant instant) {
        return granularity.bucketStart(instant);
    }

    private Instant enclosingBucketEnd(AggregationGranularity granularity, Instant instant) {
        return granularity.bucketEnd(granularity.bucketStart(instant));
    }

    private record ScopeMeter(MeterEntity meter, String region) {
    }

    private record BucketKey(DimensionType dimensionType, UUID dimensionId,
                             AggregationGranularity granularity, Instant bucketStart, Instant bucketEnd) {
    }

    private static class BucketAccumulator {
        private BigDecimal energy = BigDecimal.ZERO;
        private BigDecimal coveredEnergy = BigDecimal.ZERO;
        private BigDecimal emissions = BigDecimal.ZERO;
        private String source;
        private final Set<String> regions = new LinkedHashSet<>();
        private int hours = 0;
        private int hoursWithFactor = 0;
        private int missingFactorHours = 0;
        private boolean estimated = false;

        void add(EnergyAggregateEntity hour, CarbonIntensityObservationEntity factor, String region) {
            hours++;
            regions.add(region);
            BigDecimal hourEnergy = hour.getEnergyConsumedKwh();
            energy = energy.add(hourEnergy, INTERMEDIATE);

            if (factor == null) {
                missingFactorHours++;
                return;
            }
            hoursWithFactor++;
            coveredEnergy = coveredEnergy.add(hourEnergy, INTERMEDIATE);
            if (factor.isEstimated()) {
                estimated = true;
            }
            if (source == null) {
                source = factor.getProvider();
            } else if (!source.equals(factor.getProvider())) {
                source = "MIXED";
            }

            BigDecimal intensityKg = BigDecimal.valueOf(factor.getCarbonIntensityGCo2EqPerKwh())
                    .divide(THOUSAND, 12, RoundingMode.HALF_EVEN);
            BigDecimal hourEmissions = hourEnergy.multiply(intensityKg, INTERMEDIATE);
            emissions = emissions.add(hourEmissions, INTERMEDIATE);
        }

        String gridRegionCode() {
            return regions.size() == 1 ? regions.iterator().next() : "MIXED";
        }

        BigDecimal weightedIntensity() {
            if (coveredEnergy.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            return emissions.divide(coveredEnergy, INTERMEDIATE)
                    .multiply(THOUSAND, INTERMEDIATE)
                    .setScale(9, RoundingMode.HALF_EVEN);
        }

        BigDecimal coverage() {
            if (energy.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return coveredEnergy.divide(energy, 6, RoundingMode.HALF_EVEN);
        }

        String qualityStatus() {
            if (hoursWithFactor == 0) {
                return "UNAVAILABLE";
            }
            if (hoursWithFactor < hours) {
                return estimated ? "ESTIMATED" : "PARTIAL";
            }
            return estimated ? "ESTIMATED" : "COMPLETE";
        }
    }
}
