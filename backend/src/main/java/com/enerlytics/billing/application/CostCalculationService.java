package com.enerlytics.billing.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.billing.domain.EnergyCostEntity;
import com.enerlytics.billing.domain.TariffEntity;
import com.enerlytics.billing.domain.TariffRateEntity;
import com.enerlytics.billing.infrastructure.persistence.EnergyCostRepository;
import com.enerlytics.billing.infrastructure.persistence.TariffRepository;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.facility.infrastructure.persistence.ZoneRepository;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Effective-dated electricity cost calculation.
 *
 * <p>Computes cost bottom-up from meter-level hourly energy aggregates. For each
 * hour, the site's tariff effective on the hour's local date (in the tariff's
 * timezone) is resolved, then the applicable rate window — including
 * midnight-wrapping windows — prices the energy. Demand charges use the maximum
 * hourly peak power times the demand rate of the rate effective at that peak's
 * hour. Buckets lacking tariff or rate coverage are marked UNAVAILABLE or
 * PARTIAL; zero is never substituted for missing pricing.</p>
 */
@Service
public class CostCalculationService {

    private static final MathContext INTERMEDIATE = new MathContext(34, RoundingMode.HALF_EVEN);

    private final EnergyCostRepository costRepository;
    private final EnergyAggregateRepository energyAggregateRepository;
    private final TariffRepository tariffRepository;
    private final MeterRepository meterRepository;
    private final SiteRepository siteRepository;
    private final BuildingRepository buildingRepository;
    private final ZoneRepository zoneRepository;

    public CostCalculationService(EnergyCostRepository costRepository,
                                  EnergyAggregateRepository energyAggregateRepository,
                                  TariffRepository tariffRepository,
                                  MeterRepository meterRepository,
                                  SiteRepository siteRepository,
                                  BuildingRepository buildingRepository,
                                  ZoneRepository zoneRepository) {
        this.costRepository = costRepository;
        this.energyAggregateRepository = energyAggregateRepository;
        this.tariffRepository = tariffRepository;
        this.meterRepository = meterRepository;
        this.siteRepository = siteRepository;
        this.buildingRepository = buildingRepository;
        this.zoneRepository = zoneRepository;
    }

    /**
     * Materializes cost rows for the requested scope and time range.
     *
     * @return persisted cost rows ordered by bucket start
     */
    @Transactional
    public List<EnergyCostEntity> calculateCosts(UUID organizationId, DimensionType dimension,
                                                 UUID dimensionId, AggregationGranularity granularity,
                                                 Instant from, Instant to) {
        if (granularity == AggregationGranularity.QUARTER_HOUR) {
            throw new IllegalArgumentException("Cost calculation supports HOUR, DAY, and MONTH granularities");
        }
        validateDimension(organizationId, dimension, dimensionId);
        UUID calculationRunId = UUID.randomUUID();

        List<ScopeMeter> meters = resolveMeters(organizationId, dimension, dimensionId);
        Map<UUID, List<TariffEntity>> tariffsBySite = loadTariffs(meters);

        Map<BucketKey, BucketAccumulator> accumulators = new LinkedHashMap<>();

        for (ScopeMeter scopeMeter : meters) {
            List<EnergyAggregateEntity> energy = energyAggregateRepository
                    .findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
                            organizationId, DimensionType.METER, scopeMeter.meter.getId(),
                            AggregationGranularity.HOUR, from, to);

            for (EnergyAggregateEntity hour : energy) {
                ResolvedRate resolved = resolveRate(tariffsBySite.get(scopeMeter.siteId), hour.getBucketStart());
                BucketKey key = new BucketKey(dimension, dimensionId, granularity,
                        granularity.bucketStart(hour.getBucketStart()),
                        granularity.bucketEnd(granularity.bucketStart(hour.getBucketStart())));
                accumulators.computeIfAbsent(key, k -> new BucketAccumulator()).add(hour, resolved);
            }
        }

        List<EnergyCostEntity> results = new ArrayList<>();
        for (Map.Entry<BucketKey, BucketAccumulator> entry : accumulators.entrySet()) {
            results.add(saveBucket(organizationId, entry.getKey(), entry.getValue(), calculationRunId));
        }
        return results.stream().sorted(Comparator.comparing(EnergyCostEntity::getBucketStart)).toList();
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
        return meters.stream()
                .map(m -> new ScopeMeter(m, m.getSite() != null ? m.getSite().getId() : null))
                .toList();
    }

    private Map<UUID, List<TariffEntity>> loadTariffs(List<ScopeMeter> meters) {
        Map<UUID, List<TariffEntity>> bySite = new HashMap<>();
        for (ScopeMeter m : meters) {
            if (m.siteId != null) {
                bySite.computeIfAbsent(m.siteId, id -> tariffRepository
                        .findByOrganization_IdAndSite_IdAndActiveTrueOrderByEffectiveFromDesc(
                                m.meter.getOrganization().getId(), id));
            }
        }
        return bySite;
    }

    /**
     * Resolves the rate for an hourly bucket: the tariff effective on the hour's
     * local date in the tariff timezone, then the matching day-type/window rate
     * with highest priority. Returns null when no tariff or rate applies.
     */
    private ResolvedRate resolveRate(List<TariffEntity> tariffs, Instant hourStart) {
        if (tariffs == null || tariffs.isEmpty()) {
            return null;
        }
        for (TariffEntity tariff : tariffs) {
            ZoneId zone = ZoneId.of(tariff.getTimezone());
            ZonedDateTime local = hourStart.atZone(zone);
            if (!tariff.isEffectiveOn(local.toLocalDate())) {
                continue;
            }
            LocalDate localDate = local.toLocalDate();
            LocalTime localTime = local.toLocalTime();
            TariffRateEntity rate = tariff.getRates().stream()
                    .filter(r -> r.getDayType().appliesTo(localDate) && r.contains(localTime))
                    .max(Comparator.comparingInt(TariffRateEntity::getPriority)
                            .thenComparing(TariffRateEntity::getStartTime))
                    .orElse(null);
            return new ResolvedRate(tariff, rate);
        }
        return null;
    }

    private EnergyCostEntity saveBucket(UUID orgId, BucketKey key, BucketAccumulator acc, UUID calculationRunId) {
        EnergyCostEntity entity = costRepository
                .findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                        orgId, key.dimensionType, key.dimensionId, key.granularity, key.bucketStart)
                .orElseGet(() -> new EnergyCostEntity(orgId, key.dimensionType, key.dimensionId,
                        key.granularity, key.bucketStart, key.bucketEnd, calculationRunId));

        String quality = acc.qualityStatus();
        String currency = acc.singleCurrency();
        UUID tariffId = acc.singleTariffId();
        boolean priced = acc.hoursWithRate > 0 && currency != null;

        BigDecimal energyCost = null;
        BigDecimal demandCharge = null;
        BigDecimal totalCost = null;
        if (priced) {
            energyCost = acc.energyCost.setScale(9, RoundingMode.HALF_EVEN);
            demandCharge = acc.demandCharge != null
                    ? acc.demandCharge.setScale(9, RoundingMode.HALF_EVEN) : null;
            totalCost = energyCost.add(demandCharge != null ? demandCharge : BigDecimal.ZERO)
                    .setScale(9, RoundingMode.HALF_EVEN);
        } else if (acc.hoursWithRate > 0) {
            quality = "UNAVAILABLE";
            currency = null;
            tariffId = null;
        }

        entity.applyMetrics(tariffId, currency, acc.energy.setScale(9, RoundingMode.HALF_EVEN),
                energyCost, demandCharge, totalCost, acc.coverage(), quality,
                acc.missingRateHours, calculationRunId);
        return costRepository.save(entity);
    }

    private record ScopeMeter(MeterEntity meter, UUID siteId) {
    }

    private record ResolvedRate(TariffEntity tariff, TariffRateEntity rate) {
    }

    private record BucketKey(DimensionType dimensionType, UUID dimensionId,
                             AggregationGranularity granularity, Instant bucketStart, Instant bucketEnd) {
    }

    private static class BucketAccumulator {
        private BigDecimal energy = BigDecimal.ZERO;
        private BigDecimal coveredEnergy = BigDecimal.ZERO;
        private BigDecimal energyCost = BigDecimal.ZERO;
        private BigDecimal demandCharge;
        private final Set<String> currencies = new LinkedHashSet<>();
        private final Set<UUID> tariffIds = new LinkedHashSet<>();
        private int hours = 0;
        private int hoursWithRate = 0;
        private int missingRateHours = 0;

        void add(EnergyAggregateEntity hour, ResolvedRate resolved) {
            hours++;
            BigDecimal hourEnergy = hour.getEnergyConsumedKwh();
            energy = energy.add(hourEnergy, INTERMEDIATE);

            if (resolved == null || resolved.rate() == null) {
                missingRateHours++;
                return;
            }
            hoursWithRate++;
            coveredEnergy = coveredEnergy.add(hourEnergy, INTERMEDIATE);
            currencies.add(resolved.tariff().getCurrency());
            tariffIds.add(resolved.tariff().getId());

            energyCost = energyCost.add(
                    hourEnergy.multiply(resolved.rate().getCostPerKwh(), INTERMEDIATE), INTERMEDIATE);

            BigDecimal demandRate = resolved.rate().getDemandRatePerKw();
            BigDecimal peak = hour.getPeakPowerKw();
            if (demandRate != null && peak != null) {
                BigDecimal candidate = peak.multiply(demandRate, INTERMEDIATE);
                if (demandCharge == null || candidate.compareTo(demandCharge) > 0) {
                    demandCharge = candidate;
                }
            }
        }

        String singleCurrency() {
            return currencies.size() == 1 ? currencies.iterator().next() : null;
        }

        UUID singleTariffId() {
            return tariffIds.size() == 1 ? tariffIds.iterator().next() : null;
        }

        BigDecimal coverage() {
            if (energy.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return coveredEnergy.divide(energy, 6, RoundingMode.HALF_EVEN);
        }

        String qualityStatus() {
            if (hoursWithRate == 0) {
                return "UNAVAILABLE";
            }
            if (hoursWithRate < hours) {
                return "PARTIAL";
            }
            return "COMPLETE";
        }
    }
}
