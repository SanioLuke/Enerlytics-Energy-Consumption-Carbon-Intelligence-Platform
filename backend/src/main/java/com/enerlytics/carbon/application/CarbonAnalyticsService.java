package com.enerlytics.carbon.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.carbon.api.dto.*;
import com.enerlytics.carbon.domain.CarbonEmissionEntity;
import com.enerlytics.carbon.domain.CarbonIntensityObservationEntity;
import com.enerlytics.carbon.infrastructure.persistence.CarbonEmissionRepository;
import com.enerlytics.carbon.infrastructure.persistence.CarbonIntensityObservationRepository;
import com.enerlytics.carbon.provider.CarbonIntensityProvider;
import com.enerlytics.carbon.provider.CarbonIntensity;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CarbonAnalyticsService {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final CarbonEmissionCalculationService calculationService;
    private final CarbonEmissionRepository emissionRepository;
    private final CarbonIntensityObservationRepository observationRepository;
    private final CarbonIntensityProvider carbonIntensityProvider;
    private final SiteRepository siteRepository;

    public CarbonAnalyticsService(CarbonEmissionCalculationService calculationService,
                                  CarbonEmissionRepository emissionRepository,
                                  CarbonIntensityObservationRepository observationRepository,
                                  CarbonIntensityProvider carbonIntensityProvider,
                                  SiteRepository siteRepository) {
        this.calculationService = calculationService;
        this.emissionRepository = emissionRepository;
        this.observationRepository = observationRepository;
        this.carbonIntensityProvider = carbonIntensityProvider;
        this.siteRepository = siteRepository;
    }

    @Transactional
    public CarbonIntensityResponse currentIntensity(UUID orgId, UUID siteId) {
        SiteEntity site = siteRepository.findByIdAndOrganizationIdAndActiveTrue(siteId, orgId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));
        String zone = zoneForSite(site);
        if (zone == null) {
            throw new IllegalStateException("Site has no configured grid region or country");
        }

        CarbonIntensity latest = carbonIntensityProvider.latest(zone);
        persistIfNotPresent(latest);

        return new CarbonIntensityResponse(latest.zone(), latest.carbonIntensityGCo2EqPerKwh(),
                latest.estimated(), latest.retrievedAt());
    }

    @Transactional
    public List<CarbonEmissionResponse> todayEmissions(UUID orgId, DimensionType dimension, UUID dimensionId) {
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant tomorrowStart = todayStart.plusSeconds(86400);
        calculationService.calculateEmissions(orgId, dimension, dimensionId, AggregationGranularity.DAY,
                todayStart, tomorrowStart);
        return map(emissionRepository.findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
                orgId, dimension, dimensionId, AggregationGranularity.DAY, todayStart, tomorrowStart));
    }

    @Transactional
    public List<CarbonEmissionResponse> emissions(UUID orgId, DimensionType dimension, UUID dimensionId,
                                                   AggregationGranularity granularity, Instant from, Instant to) {
        return map(calculationService.calculateEmissions(orgId, dimension, dimensionId, granularity, from, to));
    }

    @Transactional
    public List<CarbonEmissionResponse> trend(UUID orgId, DimensionType dimension, UUID dimensionId,
                                               AggregationGranularity granularity, Instant from, Instant to) {
        return emissions(orgId, dimension, dimensionId, granularity, from, to);
    }

    @Transactional
    public List<CarbonEmissionResponse> realizedIntensity(UUID orgId, DimensionType dimension, UUID dimensionId,
                                                           AggregationGranularity granularity, Instant from, Instant to) {
        return emissions(orgId, dimension, dimensionId, granularity, from, to);
    }

    @Transactional
    public List<SiteComparisonResponse> siteComparison(UUID orgId, Instant from, Instant to) {
        List<SiteEntity> sites = siteRepository.findByOrganizationIdAndActiveTrue(orgId,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        sites.forEach(site -> calculationService.calculateEmissions(orgId, DimensionType.SITE, site.getId(),
                AggregationGranularity.DAY, from, to));

        List<CarbonEmissionEntity> emissions = emissionRepository.findTopSiteEmissions(
                orgId, AggregationGranularity.DAY, from, to);

        return sites.stream()
                .map(site -> {
                    BigDecimal siteEnergy = emissions.stream()
                            .filter(e -> e.getDimensionId().equals(site.getId()))
                            .map(CarbonEmissionEntity::getEnergyConsumedKwh)
                            .reduce(ZERO, BigDecimal::add);
                    BigDecimal siteEmissions = emissions.stream()
                            .filter(e -> e.getDimensionId().equals(site.getId()))
                            .map(CarbonEmissionEntity::getEmissionsKgCo2Eq)
                            .reduce(ZERO, BigDecimal::add);
                    BigDecimal intensity = ZERO.compareTo(siteEnergy) == 0 ? ZERO
                            : siteEmissions.divide(siteEnergy, 9, RoundingMode.HALF_EVEN).multiply(THOUSAND);
                    return new SiteComparisonResponse(site.getId(), site.getName(), site.getGridRegionCode(),
                            siteEnergy, intensity, siteEmissions);
                })
                .sorted(Comparator.comparing(SiteComparisonResponse::emissionsKgCo2Eq).reversed())
                .toList();
    }

    @Transactional
    public CarbonPerFloorAreaResponse perFloorArea(UUID orgId, UUID siteId, Instant from, Instant to) {
        SiteEntity site = siteRepository.findByIdAndOrganizationIdAndActiveTrue(siteId, orgId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));
        if (site.getFloorArea() == null || site.getFloorAreaUnit() == null) {
            throw new IllegalStateException("Site has no configured floor area");
        }
        BigDecimal areaM2 = switch (site.getFloorAreaUnit()) {
            case M2 -> site.getFloorArea();
            case FT2 -> site.getFloorArea().multiply(new BigDecimal("0.09290304"));
        };

        calculationService.calculateEmissions(orgId, DimensionType.SITE, siteId, AggregationGranularity.DAY, from, to);
        List<CarbonEmissionEntity> emissions = emissionRepository.findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
                orgId, DimensionType.SITE, siteId, AggregationGranularity.DAY, from, to);

        BigDecimal totalEnergy = emissions.stream().map(CarbonEmissionEntity::getEnergyConsumedKwh).reduce(ZERO, BigDecimal::add);
        BigDecimal totalEmissions = emissions.stream().map(CarbonEmissionEntity::getEmissionsKgCo2Eq).reduce(ZERO, BigDecimal::add);
        BigDecimal perM2 = ZERO.compareTo(areaM2) == 0 ? ZERO
                : totalEmissions.divide(areaM2, 9, RoundingMode.HALF_EVEN);

        return new CarbonPerFloorAreaResponse(site.getId(), site.getName(), areaM2, totalEnergy, totalEmissions, perM2);
    }

    @Transactional
    public List<FacilityEmissionResponse> byFacility(UUID orgId, Instant from, Instant to) {
        List<SiteEntity> sites = siteRepository.findByOrganizationIdAndActiveTrue(orgId,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        return sites.stream()
                .map(site -> {
                    calculationService.calculateEmissions(orgId, DimensionType.SITE, site.getId(),
                            AggregationGranularity.DAY, from, to);
                    return site;
                })
                .map(site -> {
                    List<CarbonEmissionEntity> emissions = emissionRepository
                            .findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
                                    orgId, DimensionType.SITE, site.getId(), AggregationGranularity.DAY, from, to);
                    BigDecimal energy = emissions.stream()
                            .map(CarbonEmissionEntity::getEnergyConsumedKwh)
                            .reduce(ZERO, BigDecimal::add);
                    BigDecimal ems = emissions.stream()
                            .map(CarbonEmissionEntity::getEmissionsKgCo2Eq)
                            .reduce(ZERO, BigDecimal::add);
                    return new FacilityEmissionResponse(DimensionType.SITE, site.getId(), site.getName(),
                            site.getGridRegionCode(), energy, ems);
                })
                .sorted(Comparator.comparing(FacilityEmissionResponse::emissionsKgCo2Eq).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderQualityResponse> providerQuality(UUID orgId, Instant from, Instant to) {
        List<String> zones = siteRepository.findByOrganizationIdAndActiveTrue(orgId,
                        org.springframework.data.domain.Pageable.unpaged()).getContent()
                .stream()
                .map(this::zoneForSite)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        java.time.Duration freshness = java.time.Duration.ofHours(24);
        return zones.stream().map(zone -> {
            List<CarbonIntensityObservationEntity> observations = observationRepository.findByZoneAndObservedAtBetween(zone, from, to);
            long totalHours = java.time.Duration.between(from, to).toHours();
            long withFactor = observations.size();
            long estimated = observations.stream().filter(CarbonIntensityObservationEntity::isEstimated).count();
            long stale = observations.stream()
                    .filter(o -> java.time.Duration.between(o.getObservedAt(), o.getRetrievedAt()).compareTo(freshness) > 0)
                    .count();
            return new ProviderQualityResponse(zone, totalHours, withFactor, estimated, totalHours - withFactor, stale);
        }).toList();
    }

    private void persistIfNotPresent(CarbonIntensity latest) {
        observationRepository.findFirstByProviderAndZoneOrderByObservedAtDesc(latest.provider(), latest.zone())
                .filter(o -> o.getObservedAt().equals(latest.timestamp()))
                .orElseGet(() -> observationRepository.save(new CarbonIntensityObservationEntity(
                        latest.provider(), latest.zone(), latest.timestamp(),
                        latest.carbonIntensityGCo2EqPerKwh(), latest.estimated(), Instant.now())));
    }

    private String zoneForSite(SiteEntity site) {
        String zone = site.getGridRegionCode();
        if (zone == null || zone.isBlank()) {
            zone = site.getCountry();
        }
        return (zone == null || zone.isBlank()) ? null : zone;
    }

    private List<CarbonEmissionResponse> map(List<CarbonEmissionEntity> entities) {
        return entities.stream().map(this::map).toList();
    }

    private CarbonEmissionResponse map(CarbonEmissionEntity e) {
        return new CarbonEmissionResponse(
                e.getOrganizationId(), e.getDimensionType(), e.getDimensionId(), e.getGranularity(),
                e.getBucketStart(), e.getBucketEnd(), e.getGridRegionCode(), e.getEnergyConsumedKwh(),
                e.getCarbonIntensityGCo2EqPerKwh(), e.getCarbonIntensitySource(), e.getEmissionsGCo2Eq(),
                e.getEmissionsKgCo2Eq(), e.getEmissionsTCo2Eq(), e.getCoverageRatio(), e.isEstimated(),
                e.getQualityStatus());
    }
}
