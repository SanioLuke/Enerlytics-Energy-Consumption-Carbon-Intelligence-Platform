package com.enerlytics.analytics;

import com.enerlytics.analytics.application.EnergyAggregationService;
import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.facility.domain.BuildingEntity;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.domain.ZoneEntity;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.facility.infrastructure.persistence.ZoneRepository;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import com.enerlytics.telemetry.domain.MeterReadingEntity;
import com.enerlytics.telemetry.infrastructure.persistence.MeterReadingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EnergyAggregationServiceTest {

    @Autowired
    private EnergyAggregationService aggregationService;

    @Autowired
    private EnergyAggregateRepository aggregateRepository;

    @Autowired
    private MeterReadingRepository readingRepository;

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private static final Instant HOUR_START = Instant.parse("2026-01-15T10:00:00Z");

    @AfterEach
    void cleanUp() {
        aggregateRepository.deleteAll();
        readingRepository.deleteAll();
        meterRepository.deleteAll();
        zoneRepository.deleteAll();
        buildingRepository.deleteAll();
        siteRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void meterHourBucketComputesExactMetrics() {
        Fixture f = fixture();
        insertReading(f.meter, HOUR_START.plusSeconds(60), "1.0", "10", "0.90");
        insertReading(f.meter, HOUR_START.plusSeconds(120), "2.0", "20", "0.95");
        insertReading(f.meter, HOUR_START.plusSeconds(180), "3.0", "30", "1.00");

        EnergyAggregateEntity agg = aggregationService.recomputeBucket(
                f.org.getId(), DimensionType.METER, f.meter.getId(), AggregationGranularity.HOUR, HOUR_START);

        assertThat(agg.getEnergyConsumedKwh()).isEqualByComparingTo("6.0");
        assertThat(agg.getAveragePowerKw()).isEqualByComparingTo("20");
        assertThat(agg.getPeakPowerKw()).isEqualByComparingTo("30");
        assertThat(agg.getMinimumPowerKw()).isEqualByComparingTo("10");
        assertThat(agg.getAveragePowerFactor()).isEqualByComparingTo("0.95");
        assertThat(agg.getReadingCount()).isEqualTo(3);
        assertThat(agg.getEstimatedReadingCount()).isEqualTo(60);
        assertThat(agg.getDataCompletenessPct()).isEqualByComparingTo("5.00");
        assertThat(agg.getBucketEnd()).isEqualTo(HOUR_START.plusSeconds(3600));
    }

    @Test
    void quarterHourBucketBoundaryIsRespected() {
        Fixture f = fixture();
        insertReading(f.meter, Instant.parse("2026-01-15T10:14:59Z"), "1.0", "10", "0.90");
        insertReading(f.meter, Instant.parse("2026-01-15T10:15:00Z"), "4.0", "40", "0.90");

        EnergyAggregateEntity first = aggregationService.recomputeBucket(
                f.org.getId(), DimensionType.METER, f.meter.getId(),
                AggregationGranularity.QUARTER_HOUR, Instant.parse("2026-01-15T10:00:00Z"));
        EnergyAggregateEntity second = aggregationService.recomputeBucket(
                f.org.getId(), DimensionType.METER, f.meter.getId(),
                AggregationGranularity.QUARTER_HOUR, Instant.parse("2026-01-15T10:15:00Z"));

        assertThat(first.getEnergyConsumedKwh()).isEqualByComparingTo("1.0");
        assertThat(first.getReadingCount()).isEqualTo(1);
        assertThat(second.getEnergyConsumedKwh()).isEqualByComparingTo("4.0");
        assertThat(second.getReadingCount()).isEqualTo(1);
        assertThat(second.getEstimatedReadingCount()).isEqualTo(15);
    }

    @Test
    void hierarchyRollupsSumAcrossMeters() {
        Fixture f = fixture();
        MeterEntity second = new MeterEntity(f.org, f.site, "M-B", "Meter B", 60);
        second.setStatus(MeterStatus.ACTIVE);
        second.setBuilding(f.building);
        second.setZone(f.zone);
        second = meterRepository.save(second);

        insertReading(f.meter, HOUR_START.plusSeconds(60), "1.0", "10", "0.90");
        insertReading(f.meter, HOUR_START.plusSeconds(120), "2.0", "20", "0.95");
        insertReading(f.meter, HOUR_START.plusSeconds(180), "3.0", "30", "1.00");
        insertReading(second, HOUR_START.plusSeconds(240), "4.0", "40", "0.80");

        for (DimensionType dim : new DimensionType[]{DimensionType.ZONE, DimensionType.BUILDING,
                DimensionType.SITE, DimensionType.ORGANIZATION}) {
            UUID dimId = switch (dim) {
                case ZONE -> f.zone.getId();
                case BUILDING -> f.building.getId();
                case SITE -> f.site.getId();
                default -> f.org.getId();
            };
            EnergyAggregateEntity agg = aggregationService.recomputeBucket(
                    f.org.getId(), dim, dimId, AggregationGranularity.HOUR, HOUR_START);
            assertThat(agg.getEnergyConsumedKwh()).as(dim + " energy").isEqualByComparingTo("10.0");
            assertThat(agg.getAveragePowerKw()).as(dim + " avgPower").isEqualByComparingTo("25");
            assertThat(agg.getPeakPowerKw()).as(dim + " peak").isEqualByComparingTo("40");
            assertThat(agg.getMinimumPowerKw()).as(dim + " min").isEqualByComparingTo("10");
            assertThat(agg.getReadingCount()).as(dim + " count").isEqualTo(4);
            assertThat(agg.getEstimatedReadingCount()).as(dim + " estimated").isEqualTo(120);
            assertThat(agg.getDataCompletenessPct()).as(dim + " completeness").isEqualByComparingTo("3.33");
        }
    }

    @Test
    void aggregateReadingUpdatesAllDimensionsAndGranularities() {
        Fixture f = fixture();
        insertReading(f.meter, HOUR_START.plusSeconds(60), "2.5", "50", "0.90");

        aggregationService.aggregateReading(f.org.getId(), f.meter.getId(), HOUR_START.plusSeconds(60));

        // 4 granularities x 5 dimensions = 20 aggregate rows.
        assertThat(aggregateRepository.findAll()).hasSize(20);

        EnergyAggregateEntity day = aggregateRepository
                .findByDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                        DimensionType.ORGANIZATION, f.org.getId(), AggregationGranularity.DAY,
                        Instant.parse("2026-01-15T00:00:00Z"))
                .orElseThrow();
        assertThat(day.getEnergyConsumedKwh()).isEqualByComparingTo("2.5");

        EnergyAggregateEntity month = aggregateRepository
                .findByDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                        DimensionType.METER, f.meter.getId(), AggregationGranularity.MONTH,
                        Instant.parse("2026-01-01T00:00:00Z"))
                .orElseThrow();
        assertThat(month.getEstimatedReadingCount()).isEqualTo(31L * 24 * 60);
    }

    @Test
    void recomputeIsIdempotentAndSelfHealing() {
        Fixture f = fixture();
        insertReading(f.meter, HOUR_START.plusSeconds(60), "1.0", "10", "0.90");

        EnergyAggregateEntity first = aggregationService.recomputeBucket(
                f.org.getId(), DimensionType.METER, f.meter.getId(), AggregationGranularity.HOUR, HOUR_START);
        aggregationService.recomputeBucket(
                f.org.getId(), DimensionType.METER, f.meter.getId(), AggregationGranularity.HOUR, HOUR_START);
        assertThat(aggregateRepository.findAll()).hasSize(1);
        assertThat(first.getEnergyConsumedKwh()).isEqualByComparingTo("1.0");

        // Late-arriving reading for the same bucket changes the stored values.
        insertReading(f.meter, HOUR_START.plusSeconds(120), "2.0", "20", "0.95");
        EnergyAggregateEntity healed = aggregationService.recomputeBucket(
                f.org.getId(), DimensionType.METER, f.meter.getId(), AggregationGranularity.HOUR, HOUR_START);

        assertThat(healed.getId()).isEqualTo(first.getId());
        assertThat(healed.getEnergyConsumedKwh()).isEqualByComparingTo("3.0");
        assertThat(healed.getReadingCount()).isEqualTo(2);
    }

    @Test
    void reconciliationPicksUpReadingsIngestedAfterAggregateWasComputed() {
        Fixture f = fixture();
        insertReading(f.meter, HOUR_START.plusSeconds(60), "1.0", "10", "0.90");

        int recomputed = aggregationService.reconcile(Instant.now().minus(Duration.ofHours(1)));

        assertThat(recomputed).isGreaterThan(0);
        EnergyAggregateEntity agg = aggregateRepository
                .findByDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                        DimensionType.METER, f.meter.getId(), AggregationGranularity.HOUR, HOUR_START)
                .orElseThrow();
        assertThat(agg.getEnergyConsumedKwh()).isEqualByComparingTo("1.0");
    }

    @Test
    void emptyBucketProducesZeroedAggregate() {
        Fixture f = fixture();

        EnergyAggregateEntity agg = aggregationService.recomputeBucket(
                f.org.getId(), DimensionType.METER, f.meter.getId(), AggregationGranularity.HOUR, HOUR_START);

        assertThat(agg.getReadingCount()).isZero();
        assertThat(agg.getEnergyConsumedKwh()).isEqualByComparingTo("0");
        assertThat(agg.getDataCompletenessPct()).isEqualByComparingTo("0");
        assertThat(agg.getAveragePowerKw()).isNull();
    }

    private Fixture fixture() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("agg-org", "Agg Org"));
        SiteEntity site = siteRepository.save(new SiteEntity(org, "S-AGG", "Agg Site", "UTC"));
        BuildingEntity building = buildingRepository.save(new BuildingEntity(org, site, "B-AGG", "Agg Building"));
        ZoneEntity zone = zoneRepository.save(new ZoneEntity(org, site, building, "Z-AGG", "Agg Zone"));
        MeterEntity meter = new MeterEntity(org, site, "M-AGG", "Agg Meter", 60);
        meter.setStatus(MeterStatus.ACTIVE);
        meter.setBuilding(building);
        meter.setZone(zone);
        return new Fixture(org, site, building, zone, meterRepository.save(meter));
    }

    private void insertReading(MeterEntity meter, Instant timestamp, String energyKwh, String powerKw, String powerFactor) {
        MeterReadingEntity reading = new MeterReadingEntity(
                meter.getOrganization().getId(), meter.getId(), timestamp,
                new BigDecimal(energyKwh), UUID.randomUUID());
        reading.setPowerKw(new BigDecimal(powerKw));
        reading.setPowerFactor(new BigDecimal(powerFactor));
        reading.setQualityStatus("OK");
        readingRepository.save(reading);
    }

    private record Fixture(OrganizationEntity org, SiteEntity site, BuildingEntity building,
                           ZoneEntity zone, MeterEntity meter) {
    }
}
