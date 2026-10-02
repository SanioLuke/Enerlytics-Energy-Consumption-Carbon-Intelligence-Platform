package com.enerlytics.billing.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.billing.domain.EnergyCostEntity;
import com.enerlytics.billing.domain.TariffDayType;
import com.enerlytics.billing.domain.TariffEntity;
import com.enerlytics.billing.domain.TariffRateEntity;
import com.enerlytics.billing.domain.TariffType;
import com.enerlytics.billing.infrastructure.persistence.EnergyCostRepository;
import com.enerlytics.billing.infrastructure.persistence.TariffRepository;
import com.enerlytics.config.JpaConfig;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.facility.infrastructure.persistence.ZoneRepository;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CostCalculationService.class, JpaConfig.class})
class CostCalculationServiceTest {

    /** Thursday 2026-01-15 10:00 UTC — a weekday. */
    private static final Instant HOUR = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private CostCalculationService calculationService;

    @Autowired
    private EnergyCostRepository costRepository;

    @Autowired
    private TariffRepository tariffRepository;

    @Autowired
    private EnergyAggregateRepository energyAggregateRepository;

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @AfterEach
    void cleanUp() {
        costRepository.deleteAll();
        tariffRepository.deleteAll();
        energyAggregateRepository.deleteAll();
        meterRepository.deleteAll();
        siteRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void flatRateBillingMultipliesEnergyByRate() {
        var ctx = setup("UTC");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("10.000"), null);
        tariff(ctx.siteId, TariffType.FLAT_RATE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "00:00", "23:59", "0.20", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results).hasSize(1);
        EnergyCostEntity row = results.get(0);
        assertThat(row.getEnergyConsumedKwh()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(row.getEnergyCost()).isEqualByComparingTo(new BigDecimal("2.000000000"));
        assertThat(row.getTotalCost()).isEqualByComparingTo(new BigDecimal("2.000000000"));
        assertThat(row.getCurrency()).isEqualTo("USD");
        assertThat(row.getQualityStatus()).isEqualTo("COMPLETE");
        assertThat(row.getCoverageRatio()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void timeOfUseSeparatesPeakAndOffPeak() {
        var ctx = setup("UTC");
        Instant offPeakHour = HOUR.plusSeconds(12 * 3600); // 22:00 UTC
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("10.000"), null);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, offPeakHour, new BigDecimal("10.000"), null);
        tariff(ctx.siteId, TariffType.TIME_OF_USE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "08:00", "20:00", "0.30", null, 0),
                rate(TariffDayType.ALL, "20:00", "08:00", "0.10", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, offPeakHour.plusSeconds(3600));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getEnergyCost()).isEqualByComparingTo(new BigDecimal("3.000000000"));
        assertThat(results.get(1).getEnergyCost()).isEqualByComparingTo(new BigDecimal("1.000000000"));

        List<EnergyCostEntity> day = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.DAY, HOUR, offPeakHour.plusSeconds(3600));
        assertThat(day).hasSize(1);
        assertThat(day.get(0).getTotalCost()).isEqualByComparingTo(new BigDecimal("4.000000000"));
    }

    @Test
    void tariffTransitionAppliesEffectiveDatedPricing() {
        var ctx = setup("UTC");
        Instant march31 = Instant.parse("2026-03-31T10:00:00Z"); // Tuesday
        Instant april1 = Instant.parse("2026-04-01T10:00:00Z");  // Wednesday
        insertEnergyAggregate(ctx.orgId, ctx.meterId, march31, new BigDecimal("10.000"), null);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, april1, new BigDecimal("10.000"), null);
        tariff(ctx.siteId, TariffType.FLAT_RATE, "UTC", "2026-01-01", "2026-03-31",
                rate(TariffDayType.ALL, "00:00", "23:59", "0.10", null, 0));
        tariff(ctx.siteId, TariffType.FLAT_RATE, "UTC", "2026-04-01", null,
                rate(TariffDayType.ALL, "00:00", "23:59", "0.50", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.SITE, ctx.siteId,
                AggregationGranularity.DAY, march31, april1.plusSeconds(3600));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getEnergyCost()).isEqualByComparingTo(new BigDecimal("1.000000000"));
        assertThat(results.get(1).getEnergyCost()).isEqualByComparingTo(new BigDecimal("5.000000000"));
    }

    @Test
    void overnightWindowCrossesMidnight() {
        var ctx = setup("UTC");
        Instant h23 = Instant.parse("2026-01-15T23:00:00Z");
        Instant h02 = Instant.parse("2026-01-16T02:00:00Z");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, h23, new BigDecimal("4.000"), null);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, h02, new BigDecimal("4.000"), null);
        tariff(ctx.siteId, TariffType.TIME_OF_USE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "22:00", "06:00", "0.25", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, h23, h02.plusSeconds(3600));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getEnergyCost()).isEqualByComparingTo(new BigDecimal("1.000000000"));
        assertThat(results.get(1).getEnergyCost()).isEqualByComparingTo(new BigDecimal("1.000000000"));
    }

    @Test
    void monthBoundaryProducesSeparateMonthBuckets() {
        var ctx = setup("UTC");
        Instant jan31 = Instant.parse("2026-01-31T10:00:00Z"); // Saturday
        Instant feb1 = Instant.parse("2026-02-01T10:00:00Z");  // Sunday
        insertEnergyAggregate(ctx.orgId, ctx.meterId, jan31, new BigDecimal("10.000"), null);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, feb1, new BigDecimal("20.000"), null);
        tariff(ctx.siteId, TariffType.FLAT_RATE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "00:00", "23:59", "0.10", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.MONTH, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-03-01T00:00:00Z"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getBucketStart()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(results.get(0).getTotalCost()).isEqualByComparingTo(new BigDecimal("1.000000000"));
        assertThat(results.get(1).getBucketStart()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
        assertThat(results.get(1).getTotalCost()).isEqualByComparingTo(new BigDecimal("2.000000000"));
    }

    @Test
    void rateWindowsAreEvaluatedInTariffTimezone() {
        // Tariff in New York: peak 09:00-17:00 local. 10:00 UTC is 05:00 EST → off-peak.
        var ctx = setup("UTC");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("10.000"), null);
        tariff(ctx.siteId, TariffType.TIME_OF_USE, "America/New_York", "2026-01-01", null,
                rate(TariffDayType.ALL, "09:00", "17:00", "0.30", null, 0),
                rate(TariffDayType.ALL, "17:00", "09:00", "0.05", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results.get(0).getEnergyCost()).isEqualByComparingTo(new BigDecimal("0.500000000"));
    }

    @Test
    void weekdayAndWeekendRatesDiffer() {
        var ctx = setup("UTC");
        Instant friday = Instant.parse("2026-01-16T10:00:00Z"); // Friday
        Instant saturday = Instant.parse("2026-01-17T10:00:00Z"); // Saturday
        insertEnergyAggregate(ctx.orgId, ctx.meterId, friday, new BigDecimal("10.000"), null);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, saturday, new BigDecimal("10.000"), null);
        tariff(ctx.siteId, TariffType.TIME_OF_USE, "UTC", "2026-01-01", null,
                rate(TariffDayType.WEEKDAY, "00:00", "23:59", "0.40", null, 0),
                rate(TariffDayType.WEEKEND, "00:00", "23:59", "0.15", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, friday, saturday.plusSeconds(3600));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getEnergyCost()).isEqualByComparingTo(new BigDecimal("4.000000000"));
        assertThat(results.get(1).getEnergyCost()).isEqualByComparingTo(new BigDecimal("1.500000000"));
    }

    @Test
    void demandChargeUsesPeakPowerTimesDemandRate() {
        var ctx = setup("UTC");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("10.000"), new BigDecimal("12.000"));
        tariff(ctx.siteId, TariffType.TIME_OF_USE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "00:00", "23:59", "0.20", new BigDecimal("15.00"), 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        EnergyCostEntity row = results.get(0);
        assertThat(row.getEnergyCost()).isEqualByComparingTo(new BigDecimal("2.000000000"));
        assertThat(row.getDemandCharge()).isEqualByComparingTo(new BigDecimal("180.000000000"));
        assertThat(row.getTotalCost()).isEqualByComparingTo(new BigDecimal("182.000000000"));
    }

    @Test
    void roundingFollowsHalfEvenAtScale9() {
        var ctx = setup("UTC");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("3.333"), null);
        tariff(ctx.siteId, TariffType.FLAT_RATE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "00:00", "23:59", "0.123456789", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        // 3.333 * 0.123456789 = 0.411481477737 -> 0.411481478
        assertThat(results.get(0).getEnergyCost()).isEqualByComparingTo(new BigDecimal("0.411481478"));
    }

    @Test
    void missingTariffYieldsUnavailableNotZero() {
        var ctx = setup("UTC");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("10.000"), null);

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        EnergyCostEntity row = results.get(0);
        assertThat(row.getQualityStatus()).isEqualTo("UNAVAILABLE");
        assertThat(row.getEnergyCost()).isNull();
        assertThat(row.getMissingRateHours()).isEqualTo(1);
        assertThat(row.getCoverageRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void gapInRateCoverageYieldsPartial() {
        var ctx = setup("UTC");
        Instant uncovered = HOUR.plusSeconds(3600); // 11:00, outside 08-10 window
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("10.000"), null);
        insertEnergyAggregate(ctx.orgId, ctx.meterId, uncovered, new BigDecimal("10.000"), null);
        tariff(ctx.siteId, TariffType.TIME_OF_USE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "09:00", "11:00", "0.20", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.DAY, HOUR, uncovered.plusSeconds(3600));

        assertThat(results).hasSize(1);
        EnergyCostEntity row = results.get(0);
        assertThat(row.getQualityStatus()).isEqualTo("PARTIAL");
        assertThat(row.getCoverageRatio()).isEqualByComparingTo(new BigDecimal("0.500000"));
        assertThat(row.getEnergyCost()).isEqualByComparingTo(new BigDecimal("2.000000000"));
    }

    @Test
    void higherPriorityRateWinsOnOverlap() {
        var ctx = setup("UTC");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("10.000"), null);
        tariff(ctx.siteId, TariffType.TIME_OF_USE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "00:00", "23:59", "0.20", null, 0),
                rate(TariffDayType.ALL, "09:00", "12:00", "0.90", null, 10));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results.get(0).getEnergyCost()).isEqualByComparingTo(new BigDecimal("9.000000000"));
    }

    @Test
    void recalculationIsIdempotentAndTracksNewRun() {
        var ctx = setup("UTC");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("10.000"), null);
        tariff(ctx.siteId, TariffType.FLAT_RATE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "00:00", "23:59", "0.20", null, 0));

        List<EnergyCostEntity> first = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));
        UUID firstRunId = first.get(0).getCalculationRunId();

        List<EnergyCostEntity> second = calculate(ctx, DimensionType.METER, ctx.meterId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(costRepository.count()).isEqualTo(1);
        assertThat(second.get(0).getTotalCost()).isEqualByComparingTo(new BigDecimal("2.000000000"));
        assertThat(second.get(0).getCalculationRunId()).isNotEqualTo(firstRunId);
    }

    @Test
    void organizationRollupSumsAcrossMeters() {
        var ctx = setup("UTC");
        insertEnergyAggregate(ctx.orgId, ctx.meterId, HOUR, new BigDecimal("10.000"), null);
        insertEnergyAggregate(ctx.orgId, ctx.meterId2, HOUR, new BigDecimal("30.000"), null);
        tariff(ctx.siteId, TariffType.FLAT_RATE, "UTC", "2026-01-01", null,
                rate(TariffDayType.ALL, "00:00", "23:59", "0.25", null, 0));

        List<EnergyCostEntity> results = calculate(ctx, DimensionType.ORGANIZATION, ctx.orgId,
                AggregationGranularity.HOUR, HOUR, HOUR.plusSeconds(3600));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTotalCost()).isEqualByComparingTo(new BigDecimal("10.000000000"));
        assertThat(results.get(0).getEnergyConsumedKwh()).isEqualByComparingTo(new BigDecimal("40.000"));
    }

    private List<EnergyCostEntity> calculate(TestContext ctx, DimensionType dimension, UUID dimensionId,
                                             AggregationGranularity granularity, Instant from, Instant to) {
        return calculationService.calculateCosts(ctx.orgId, dimension, dimensionId, granularity, from, to);
    }

    private TestContext setup(String siteTimezone) {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("test-org", "Test Org"));
        SiteEntity site = siteRepository.save(new SiteEntity(org, "S-1", "Site 1", siteTimezone));
        MeterEntity m1 = new MeterEntity(org, site, "M-1", "Meter 1", 60);
        m1.setStatus(MeterStatus.ACTIVE);
        m1 = meterRepository.save(m1);
        MeterEntity m2 = new MeterEntity(org, site, "M-2", "Meter 2", 60);
        m2.setStatus(MeterStatus.ACTIVE);
        m2 = meterRepository.save(m2);
        return new TestContext(org.getId(), site.getId(), m1.getId(), m2.getId());
    }

    private TariffEntity tariff(UUID siteId, TariffType type, String timezone,
                                String from, String to, TariffRateEntity... rates) {
        OrganizationEntity org = organizationRepository.findAll().get(0);
        SiteEntity site = siteRepository.findById(siteId).orElseThrow();
        TariffEntity tariff = new TariffEntity(org, site, "Tariff-" + UUID.randomUUID().toString().substring(0, 6),
                type, "USD", timezone, LocalDate.parse(from), to != null ? LocalDate.parse(to) : null);
        for (TariffRateEntity rate : rates) {
            tariff.addRate(rate);
        }
        return tariffRepository.save(tariff);
    }

    private TariffRateEntity rate(TariffDayType dayType, String start, String end,
                                  String costPerKwh, BigDecimal demandRate, int priority) {
        return new TariffRateEntity(dayType, LocalTime.parse(start), LocalTime.parse(end),
                new BigDecimal(costPerKwh), demandRate, priority);
    }

    private void insertEnergyAggregate(UUID orgId, UUID meterId, Instant start,
                                       BigDecimal energy, BigDecimal peakKw) {
        EnergyAggregateEntity agg = new EnergyAggregateEntity(orgId, DimensionType.METER, meterId,
                AggregationGranularity.HOUR, start, start.plusSeconds(3600));
        agg.applyMetrics(energy, BigDecimal.ZERO, peakKw, BigDecimal.ZERO, BigDecimal.ONE, 1L, 0L,
                BigDecimal.valueOf(100), Instant.now());
        energyAggregateRepository.save(agg);
    }

    private record TestContext(UUID orgId, UUID siteId, UUID meterId, UUID meterId2) {
    }
}
