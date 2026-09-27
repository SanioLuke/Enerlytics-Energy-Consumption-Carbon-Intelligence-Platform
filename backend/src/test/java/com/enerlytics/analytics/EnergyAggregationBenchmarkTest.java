package com.enerlytics.analytics;

import com.enerlytics.analytics.application.EnergyAggregationService;
import com.enerlytics.analytics.application.EnergyAnalyticsService;
import com.enerlytics.analytics.api.dto.EnergyAggregateResponse;
import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmarks the aggregation write path and dashboard read path against a
 * representative dataset: 100 meters, 30 days, 15-minute cadence
 * (288,000 readings).
 */
@SpringBootTest
@ActiveProfiles("test")
class EnergyAggregationBenchmarkTest {

    private static final int METER_COUNT = 100;
    private static final int DAYS = 14;
    private static final int READINGS_PER_DAY = 96;
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EnergyAggregationService aggregationService;

    @Autowired
    private EnergyAnalyticsService analyticsService;

    @Autowired
    private EnergyAggregateRepository aggregateRepository;

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM analytics.energy_aggregate");
        jdbcTemplate.update("DELETE FROM telemetry.meter_reading");
        meterRepository.deleteAll();
        siteRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void benchmarkAggregationAndReadPaths() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("bench", "Bench Org"));
        SiteEntity site = siteRepository.save(new SiteEntity(org, "S-BENCH", "Bench Site", "UTC"));

        List<UUID> meterIds = new ArrayList<>();
        for (int i = 0; i < METER_COUNT; i++) {
            MeterEntity meter = new MeterEntity(org, site, "BM-" + i, "Bench Meter " + i, 900);
            meter.setStatus(MeterStatus.ACTIVE);
            meterIds.add(meterRepository.save(meter).getId());
        }

        int totalReadings = METER_COUNT * DAYS * READINGS_PER_DAY;
        long insertStart = System.nanoTime();
        jdbcTemplate.batchUpdate("""
                INSERT INTO telemetry.meter_reading
                    (id, organization_id, meter_id, sample_timestamp, energy_kwh, power_kw,
                     voltage, current, power_factor, frequency, quality_status, source_event_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OK', ?, CURRENT_TIMESTAMP)
                """, batchArgs(org.getId(), meterIds));
        long insertMs = (System.nanoTime() - insertStart) / 1_000_000;

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM telemetry.meter_reading", Integer.class);
        assertThat(count).isEqualTo(totalReadings);

        // Aggregate write path: recompute one full month at each level.
        Instant monthStart = BASE;
        long t0 = System.nanoTime();
        aggregationService.recomputeBucket(org.getId(), DimensionType.ORGANIZATION, org.getId(),
                AggregationGranularity.MONTH, monthStart);
        long orgMonthMs = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        aggregationService.recomputeBucket(org.getId(), DimensionType.SITE, site.getId(),
                AggregationGranularity.MONTH, monthStart);
        long siteMonthMs = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        aggregationService.recomputeBucket(org.getId(), DimensionType.METER, meterIds.get(0),
                AggregationGranularity.MONTH, monthStart);
        long meterMonthMs = (System.nanoTime() - t0) / 1_000_000;

        // Dashboard read path before hour buckets are populated.
        t0 = System.nanoTime();
        List<EnergyAggregateResponse> series = analyticsService.series(
                org.getId(), DimensionType.ORGANIZATION, null, AggregationGranularity.HOUR,
                BASE, BASE.plusSeconds(DAYS * 86400L));
        long emptySeriesMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(series).isEmpty();

        // Simulate the per-reading write path on a busy hour.
        Instant busyHour = BASE.plusSeconds(10 * 3600);
        t0 = System.nanoTime();
        aggregationService.aggregateReading(org.getId(), meterIds.get(0), busyHour);
        long perEventMs = (System.nanoTime() - t0) / 1_000_000;

        // Populate all hour buckets at org level, then re-read.
        t0 = System.nanoTime();
        Instant cursor = BASE;
        while (cursor.isBefore(BASE.plusSeconds(DAYS * 86400L))) {
            aggregationService.recomputeBucket(org.getId(), DimensionType.ORGANIZATION, org.getId(),
                    AggregationGranularity.HOUR, cursor);
            cursor = cursor.plusSeconds(3600);
        }
        long hourRebuildMs = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        series = analyticsService.series(org.getId(), DimensionType.ORGANIZATION, null,
                AggregationGranularity.HOUR, BASE, BASE.plusSeconds(DAYS * 86400L));
        long seriesReadMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(series).hasSize(DAYS * 24);
        BigDecimal expectedPerHour = BigDecimal.valueOf(0.25)
                .multiply(BigDecimal.valueOf(4L * METER_COUNT));
        assertThat(series.get(0).energyConsumedKwh()).isEqualByComparingTo(expectedPerHour);
        assertThat(series.get(0).readingCount()).isEqualTo((long) (4L * METER_COUNT));

        System.out.printf("""
                %n=== ENERGY AGGREGATION BENCHMARK ===
                Dataset: %d meters x %d days x %d readings/day = %d readings
                Bulk insert:                    %d ms
                Org-level MONTH recompute:      %d ms
                Site-level MONTH recompute:     %d ms
                Meter-level MONTH recompute:    %d ms
                Per-event incremental update:   %d ms  (20 bucket recomputes)
                Org HOUR rebuild x%d buckets:   %d ms  (avg %d ms/bucket)
                Series read (%d buckets):       %d ms (empty result: %d ms)
                ====================================%n""",
                METER_COUNT, DAYS, READINGS_PER_DAY, totalReadings,
                insertMs, orgMonthMs, siteMonthMs, meterMonthMs, perEventMs,
                DAYS * 24, hourRebuildMs, hourRebuildMs / (DAYS * 24),
                series.size(), seriesReadMs, emptySeriesMs);
    }

    private List<Object[]> batchArgs(UUID orgId, List<UUID> meterIds) {
        List<Object[]> batch = new ArrayList<>(METER_COUNT * DAYS * READINGS_PER_DAY);
        for (UUID meterId : meterIds) {
            for (int d = 0; d < DAYS; d++) {
                for (int r = 0; r < READINGS_PER_DAY; r++) {
                    Instant ts = BASE.plusSeconds((long) d * 86400 + (long) r * 900);
                    batch.add(new Object[]{
                            UUID.randomUUID(), orgId, meterId, Timestamp.from(ts),
                            BigDecimal.valueOf(0.25), BigDecimal.valueOf(4.0),
                            BigDecimal.valueOf(230), BigDecimal.valueOf(10),
                            BigDecimal.valueOf(0.95), BigDecimal.valueOf(50),
                            UUID.randomUUID()
                    });
                }
            }
        }
        return batch;
    }
}
