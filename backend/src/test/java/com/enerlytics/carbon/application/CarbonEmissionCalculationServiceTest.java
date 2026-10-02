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
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.config.JpaConfig;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CarbonEmissionCalculationService.class, JpaConfig.class})
class CarbonEmissionCalculationServiceTest {

    private static final Instant HOUR = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private CarbonEmissionCalculationService calculationService;

    @Autowired
    private CarbonEmissionRepository emissionRepository;

    @Autowired
    private CarbonIntensityObservationRepository observationRepository;

    @Autowired
    private EnergyAggregateRepository energyAggregateRepository;

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @AfterEach
    void cleanUp() {
        emissionRepository.deleteAll();
        observationRepository.deleteAll();
        energyAggregateRepository.deleteAll();
        meterRepository.deleteAll();
        zoneRepository.deleteAll();
        buildingRepository.deleteAll();
        siteRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void meterEmissionsAreCalculatedDeterministically() {
        var ctx = setup("DE", 1);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("10.000"));
        observationRepository.save(observation("DE", HOUR, 420, false));

        List<CarbonEmissionEntity> results = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.METER, ctx.meterId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results).hasSize(1);
        CarbonEmissionEntity result = results.get(0);
        assertThat(result.getEnergyConsumedKwh()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(result.getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("4.200000000"));
        assertThat(result.getEmissionsGCo2Eq()).isEqualByComparingTo(new BigDecimal("4200.000000000"));
        assertThat(result.getEmissionsTCo2Eq()).isEqualByComparingTo(new BigDecimal("0.004200000"));
        assertThat(result.getCarbonIntensityGCo2EqPerKwh()).isEqualByComparingTo(new BigDecimal("420.000000000"));
        assertThat(result.getQualityStatus()).isEqualTo("COMPLETE");
        assertThat(result.isEstimated()).isFalse();
    }

    @Test
    void siteEmissionsRollUpAcrossMeters() {
        var ctx = setup("DE", 2);
        insertEnergyAggregate(ctx.orgId, ctx.meterIds.get(0), DimensionType.METER, ctx.meterIds.get(0), HOUR, new BigDecimal("5.000"));
        insertEnergyAggregate(ctx.orgId, ctx.meterIds.get(1), DimensionType.METER, ctx.meterIds.get(1), HOUR, new BigDecimal("15.000"));
        observationRepository.save(observation("DE", HOUR, 400, false));

        List<CarbonEmissionEntity> results = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.SITE, ctx.siteId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results).hasSize(1);
        CarbonEmissionEntity result = results.get(0);
        assertThat(result.getEnergyConsumedKwh()).isEqualByComparingTo(new BigDecimal("20.000"));
        assertThat(result.getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("8.000000000"));
        assertThat(result.getGridRegionCode()).isEqualTo("DE");
    }

    @Test
    void organizationEmissionsRollUpAllMeters() {
        var ctx = setup("FR", 1);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("100.000"));
        observationRepository.save(observation("FR", HOUR, 250, false));

        List<CarbonEmissionEntity> results = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.ORGANIZATION, ctx.orgId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("25.000000000"));
    }

    @Test
    void missingCarbonFactorYieldsUnavailableStatusAndNoFabricatedEmissions() {
        var ctx = setup("DE", 1);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("10.000"));

        List<CarbonEmissionEntity> results = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.METER, ctx.meterId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results).hasSize(1);
        CarbonEmissionEntity result = results.get(0);
        assertThat(result.getEmissionsKgCo2Eq()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getQualityStatus()).isEqualTo("UNAVAILABLE");
        assertThat(result.getCoverageRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void estimatedFactorMarksEmissionAsEstimated() {
        var ctx = setup("DE", 1);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("8.000"));
        observationRepository.save(observation("DE", HOUR, 300, true));

        List<CarbonEmissionEntity> results = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.METER, ctx.meterId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results.get(0).isEstimated()).isTrue();
        assertThat(results.get(0).getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("2.400000000"));
    }

    @Test
    void lateReadingRecalculationUpdatesEmissions() {
        var ctx = setup("DE", 1);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("10.000"));
        observationRepository.save(observation("DE", HOUR, 420, false));
        calculationService.calculateEmissions(ctx.orgId, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        EnergyAggregateEntity aggregate = energyAggregateRepository
                .findByDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                        DimensionType.METER, ctx.meterId, AggregationGranularity.HOUR, HOUR)
                .orElseThrow();
        aggregate.applyMetrics(new BigDecimal("20.000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, 2L, 0L, BigDecimal.valueOf(100), Instant.now());
        energyAggregateRepository.save(aggregate);

        List<CarbonEmissionEntity> recalculated = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.METER, ctx.meterId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(recalculated.get(0).getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("8.400000000"));
        assertThat(emissionRepository.count()).isEqualTo(1);
    }

    @Test
    void staleFactorYieldsUnavailableStatus() {
        var ctx = setup("DE", 1);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("10.000"));
        observationRepository.save(observation("DE", HOUR.minusSeconds(25 * 3600), 420, false));

        List<CarbonEmissionEntity> results = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.METER, ctx.meterId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results).hasSize(1);
        CarbonEmissionEntity result = results.get(0);
        assertThat(result.getQualityStatus()).isEqualTo("UNAVAILABLE");
        assertThat(result.getEmissionsKgCo2Eq()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getMissingFactorHours()).isEqualTo(1);
    }

    @Test
    void partialCoverageIsEnergyWeighted() {
        var ctx = setup("DE", 1);
        Instant secondHour = Instant.parse("2026-01-17T10:00:00Z");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("10.000"));
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, secondHour, new BigDecimal("15.000"));
        observationRepository.save(observation("DE", HOUR, 400, false));

        List<CarbonEmissionEntity> results = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.METER, ctx.meterId, AggregationGranularity.MONTH,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"));

        assertThat(results).hasSize(1);
        CarbonEmissionEntity result = results.get(0);
        assertThat(result.getEnergyConsumedKwh()).isEqualByComparingTo(new BigDecimal("25.000"));
        assertThat(result.getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("4.000000000"));
        assertThat(result.getCoverageRatio()).isEqualByComparingTo(new BigDecimal("0.400000"));
        assertThat(result.getQualityStatus()).isEqualTo("PARTIAL");
        assertThat(result.getMissingFactorHours()).isEqualTo(1);
        assertThat(result.getCarbonIntensityGCo2EqPerKwh()).isEqualByComparingTo(new BigDecimal("400.000000000"));
    }

    @Test
    void buildingEmissionsRollUpAcrossMeters() {
        var ctx = setup("DE", 2);
        OrganizationEntity org = organizationRepository.findById(ctx.orgId).orElseThrow();
        SiteEntity site = siteRepository.findById(ctx.siteId).orElseThrow();
        var building = buildingRepository.save(
                new com.enerlytics.facility.domain.BuildingEntity(org, site, "B-1", "Building 1"));
        MeterEntity m1 = meterRepository.findById(ctx.meterIds.get(0)).orElseThrow();
        m1.setBuilding(building);
        meterRepository.save(m1);
        MeterEntity m2 = meterRepository.findById(ctx.meterIds.get(1)).orElseThrow();
        m2.setBuilding(building);
        meterRepository.save(m2);

        insertEnergyAggregate(ctx.orgId, ctx.meterIds.get(0), DimensionType.METER, ctx.meterIds.get(0), HOUR, new BigDecimal("4.000"));
        insertEnergyAggregate(ctx.orgId, ctx.meterIds.get(1), DimensionType.METER, ctx.meterIds.get(1), HOUR, new BigDecimal("6.000"));
        observationRepository.save(observation("DE", HOUR, 500, false));

        List<CarbonEmissionEntity> results = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.BUILDING, building.getId(), AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results).hasSize(1);
        CarbonEmissionEntity result = results.get(0);
        assertThat(result.getEnergyConsumedKwh()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(result.getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("5.000000000"));
    }

    @Test
    void mixedRegionOrganizationUsesEnergyWeightedIntensity() {
        var ctx = setup("DE", 1);
        OrganizationEntity org = organizationRepository.findById(ctx.orgId).orElseThrow();
        SiteEntity frSite = new SiteEntity(org, "S-FR", "France Site", "UTC");
        frSite.setGridRegionCode("FR");
        frSite = siteRepository.save(frSite);
        MeterEntity frMeter = new MeterEntity(org, frSite, "M-FR", "France Meter", 60);
        frMeter.setStatus(MeterStatus.ACTIVE);
        frMeter = meterRepository.save(frMeter);

        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("10.000"));
        insertEnergyAggregate(ctx.orgId, frMeter.getId(), DimensionType.METER, frMeter.getId(), HOUR, new BigDecimal("30.000"));
        observationRepository.save(observation("DE", HOUR, 400, false));
        observationRepository.save(observation("FR", HOUR, 200, false));

        List<CarbonEmissionEntity> results = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.ORGANIZATION, ctx.orgId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results).hasSize(1);
        CarbonEmissionEntity result = results.get(0);
        assertThat(result.getEnergyConsumedKwh()).isEqualByComparingTo(new BigDecimal("40.000"));
        assertThat(result.getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("10.000000000"));
        assertThat(result.getCarbonIntensityGCo2EqPerKwh()).isEqualByComparingTo(new BigDecimal("250.000000000"));
        assertThat(result.getGridRegionCode()).isEqualTo("MIXED");
    }

    @Test
    void correctedObservationRecomputesEmissions() {
        var ctx = setup("DE", 1);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("10.000"));
        Instant retrieved = Instant.parse("2026-01-15T11:05:00Z");
        observationRepository.save(new CarbonIntensityObservationEntity("MOCK", "DE", HOUR, 400, false, retrieved));

        List<CarbonEmissionEntity> initial = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.METER, ctx.meterId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));
        assertThat(initial.get(0).getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("4.000000000"));
        UUID initialRunId = initial.get(0).getCalculationRunId();

        observationRepository.save(new CarbonIntensityObservationEntity(
                "ELECTRICITY_MAPS", "DE", HOUR, 500, false, retrieved.plusSeconds(3600)));

        List<CarbonEmissionEntity> corrected = calculationService.calculateEmissions(
                ctx.orgId, DimensionType.METER, ctx.meterId, AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(corrected).hasSize(1);
        assertThat(corrected.get(0).getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("5.000000000"));
        assertThat(corrected.get(0).getCalculationRunId()).isNotEqualTo(initialRunId);
        assertThat(emissionRepository.count()).isEqualTo(1);
    }

    @Test
    void recalculationIsIdempotent() {
        var ctx = setup("DE", 1);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, DimensionType.METER, ctx.meterId, HOUR, new BigDecimal("10.000"));
        observationRepository.save(observation("DE", HOUR, 420, false));

        calculationService.calculateEmissions(ctx.orgId, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));
        calculationService.calculateEmissions(ctx.orgId, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        List<CarbonEmissionEntity> all = emissionRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getEmissionsKgCo2Eq()).isEqualByComparingTo(new BigDecimal("4.200000000"));
    }

    private TestContext setup(String zone, int meterCount) {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("test-org", "Test Org"));
        SiteEntity site = new SiteEntity(org, "S-1", "Site 1", "UTC");
        site.setGridRegionCode(zone);
        site = siteRepository.save(site);

        TestContext ctx = new TestContext(org.getId(), site.getId());
        for (int i = 0; i < meterCount; i++) {
            MeterEntity meter = new MeterEntity(org, site, "M-" + i, "Meter " + i, 60);
            meter.setStatus(MeterStatus.ACTIVE);
            meter = meterRepository.save(meter);
            ctx.meterIds.add(meter.getId());
        }
        if (!ctx.meterIds.isEmpty()) {
            ctx.meterId = ctx.meterIds.get(0);
        }
        return ctx;
    }

    private void insertEnergyAggregate(UUID orgId, UUID meterId, DimensionType dimensionType, UUID dimensionId,
                                       Instant start, BigDecimal energy) {
        Instant end = start.plusSeconds(3600);
        EnergyAggregateEntity agg = new EnergyAggregateEntity(orgId, dimensionType, dimensionId,
                AggregationGranularity.HOUR, start, end);
        agg.applyMetrics(energy, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 1L, 0L,
                BigDecimal.valueOf(100), Instant.now());
        energyAggregateRepository.save(agg);
    }

    private CarbonIntensityObservationEntity observation(String zone, Instant timestamp, int value, boolean estimated) {
        return new CarbonIntensityObservationEntity("MOCK", zone, timestamp, value, estimated, Instant.now());
    }

    private static class TestContext {
        final UUID orgId;
        final UUID siteId;
        UUID meterId;
        final List<UUID> meterIds = new java.util.ArrayList<>();

        TestContext(UUID orgId, UUID siteId) {
            this.orgId = orgId;
            this.siteId = siteId;
        }
    }
}
