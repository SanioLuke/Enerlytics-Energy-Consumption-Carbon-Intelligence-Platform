package com.enerlytics.analytics.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Computes and maintains energy rollups in {@code analytics.energy_aggregate}.
 *
 * <p>Aggregation is <b>recompute-based</b>: every write path recomputes the
 * affected (dimension, granularity, bucket) row directly from
 * {@code telemetry.meter_reading}. Recomputing a bucket is idempotent and
 * self-healing — duplicate events, late events, and out-of-order arrivals all
 * converge to the same stored values.</p>
 *
 * <p>Per-dimension SQL aggregates readings joined to {@code telemetry.meter},
 * so parent dimensions stay correct even when a meter's location assignment
 * changes. Read paths never scan {@code meter_reading}.</p>
 */
@Service
public class EnergyAggregationService {

    private static final Logger log = LoggerFactory.getLogger(EnergyAggregationService.class);

    private static final Map<DimensionType, String> READING_PREDICATE = Map.of(
            DimensionType.METER, "r.meter_id = :dimId",
            DimensionType.ZONE, "m.zone_id = :dimId",
            DimensionType.BUILDING, "m.building_id = :dimId",
            DimensionType.SITE, "m.site_id = :dimId",
            DimensionType.ORGANIZATION, "m.organization_id = :dimId");

    private static final Map<DimensionType, String> METER_PREDICATE = Map.of(
            DimensionType.METER, "m.id = :dimId",
            DimensionType.ZONE, "m.zone_id = :dimId",
            DimensionType.BUILDING, "m.building_id = :dimId",
            DimensionType.SITE, "m.site_id = :dimId",
            DimensionType.ORGANIZATION, "m.organization_id = :dimId");

    private static final String AGGREGATE_SQL = """
            SELECT COALESCE(SUM(r.energy_kwh), 0),
                   AVG(r.power_kw),
                   MAX(r.power_kw),
                   MIN(r.power_kw),
                   AVG(r.power_factor),
                   COUNT(*)
            FROM telemetry.meter_reading r
            JOIN telemetry.meter m ON m.id = r.meter_id
            WHERE r.organization_id = :orgId
              AND r.sample_timestamp >= :bucketStart
              AND r.sample_timestamp < :bucketEnd
              AND %s
            """;

    private static final String ESTIMATED_SQL = """
            SELECT COALESCE(SUM(FLOOR(CAST(:bucketSeconds AS DOUBLE) / m.reading_interval_seconds)), 0)
            FROM telemetry.meter m
            WHERE m.organization_id = :orgId
              AND m.status <> 'DECOMMISSIONED'
              AND %s
            """;

    private final EntityManager entityManager;
    private final EnergyAggregateRepository aggregateRepository;

    public EnergyAggregationService(EntityManager entityManager,
                                    EnergyAggregateRepository aggregateRepository) {
        this.entityManager = entityManager;
        this.aggregateRepository = aggregateRepository;
    }

    /**
     * Recomputes every (granularity, dimension) bucket containing the reading's
     * timestamp for the meter and all of its ancestors in the hierarchy.
     */
    @Transactional
    public void aggregateReading(UUID organizationId, UUID meterId, Instant sampleTimestamp) {
        MeterHierarchy hierarchy = meterHierarchy(organizationId, meterId);
        if (hierarchy == null) {
            log.warn("Skipping aggregation for unknown meter {}", meterId);
            return;
        }
        for (AggregationGranularity granularity : AggregationGranularity.values()) {
            Instant bucketStart = granularity.bucketStart(sampleTimestamp);
            recomputeBucket(organizationId, DimensionType.METER, meterId, granularity, bucketStart);
            if (hierarchy.zoneId != null) {
                recomputeBucket(organizationId, DimensionType.ZONE, hierarchy.zoneId, granularity, bucketStart);
            }
            if (hierarchy.buildingId != null) {
                recomputeBucket(organizationId, DimensionType.BUILDING, hierarchy.buildingId, granularity, bucketStart);
            }
            recomputeBucket(organizationId, DimensionType.SITE, hierarchy.siteId, granularity, bucketStart);
            recomputeBucket(organizationId, DimensionType.ORGANIZATION, organizationId, granularity, bucketStart);
        }
    }

    /**
     * Recomputes a single bucket from {@code meter_reading} and upserts the
     * aggregate row. Safe to call repeatedly — results depend only on the
     * current contents of the readings table.
     */
    @Transactional
    public EnergyAggregateEntity recomputeBucket(UUID organizationId, DimensionType dimensionType, UUID dimensionId,
                                                 AggregationGranularity granularity, Instant bucketStart) {
        Instant bucketEnd = granularity.bucketEnd(bucketStart);

        Object[] row = (Object[]) entityManager.createNativeQuery(
                        AGGREGATE_SQL.formatted(READING_PREDICATE.get(dimensionType)))
                .setParameter("orgId", organizationId)
                .setParameter("dimId", dimensionId)
                .setParameter("bucketStart", bucketStart)
                .setParameter("bucketEnd", bucketEnd)
                .getSingleResult();

        long readingCount = ((Number) row[5]).longValue();
        BigDecimal energy = toBigDecimal(row[0]);
        BigDecimal avgPower = toBigDecimal(row[1]);
        BigDecimal peakPower = toBigDecimal(row[2]);
        BigDecimal minPower = toBigDecimal(row[3]);
        BigDecimal avgPowerFactor = toBigDecimal(row[4]);

        long bucketSeconds = Duration.between(bucketStart, bucketEnd).getSeconds();
        Number estimated = (Number) entityManager.createNativeQuery(
                        ESTIMATED_SQL.formatted(METER_PREDICATE.get(dimensionType)))
                .setParameter("orgId", organizationId)
                .setParameter("dimId", dimensionId)
                .setParameter("bucketSeconds", bucketSeconds)
                .getSingleResult();
        long estimatedReadingCount = estimated.longValue();

        BigDecimal completeness = estimatedReadingCount > 0
                ? BigDecimal.valueOf(readingCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(estimatedReadingCount), 2, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100))
                : (readingCount > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO);

        EnergyAggregateEntity aggregate = aggregateRepository
                .findByDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                        dimensionType, dimensionId, granularity, bucketStart)
                .orElseGet(() -> new EnergyAggregateEntity(
                        organizationId, dimensionType, dimensionId, granularity, bucketStart, bucketEnd));

        aggregate.applyMetrics(energy, avgPower, peakPower, minPower, avgPowerFactor,
                readingCount, estimatedReadingCount, completeness, Instant.now());
        return aggregateRepository.save(aggregate);
    }

    /**
     * Recomputes all buckets that intersect [oldestReading, now] for every meter
     * that ingested readings since {@code since}. Reconciliation only touches
     * meters that actually received data in the window.
     */
    @Transactional
    public int reconcile(Instant since) {
        List<?> meterWindows = entityManager.createNativeQuery("""
                SELECT r.meter_id, MIN(r.sample_timestamp), MAX(r.sample_timestamp)
                FROM telemetry.meter_reading r
                WHERE r.created_at >= :since
                GROUP BY r.meter_id
                """).setParameter("since", since).getResultList();

        int recomputed = 0;
        for (Object row : meterWindows) {
            Object[] window = (Object[]) row;
            UUID meterId = toUuid(window[0]);
            Instant oldest = toInstant(window[1]);
            Instant newest = toInstant(window[2]);
            UUID orgId = organizationIdForMeter(meterId);
            if (orgId == null || oldest == null) {
                continue;
            }
            recomputed += reconcileMeter(orgId, meterId, oldest, newest);
        }
        if (recomputed > 0) {
            log.info("Energy reconciliation recomputed {} aggregate buckets since {}", recomputed, since);
        }
        return recomputed;
    }

    private int reconcileMeter(UUID orgId, UUID meterId, Instant oldest, Instant newest) {
        MeterHierarchy hierarchy = meterHierarchy(orgId, meterId);
        if (hierarchy == null) {
            return 0;
        }
        Set<Bucket> buckets = new LinkedHashSet<>();
        for (AggregationGranularity g : AggregationGranularity.values()) {
            Instant cursor = g.bucketStart(oldest);
            Instant end = g.bucketStart(newest);
            while (!cursor.isAfter(end)) {
                buckets.add(new Bucket(g, cursor));
                cursor = g.bucketEnd(cursor);
            }
        }
        for (Bucket bucket : buckets) {
            recomputeBucket(orgId, DimensionType.METER, meterId, bucket.granularity, bucket.start);
            if (hierarchy.zoneId != null) {
                recomputeBucket(orgId, DimensionType.ZONE, hierarchy.zoneId, bucket.granularity, bucket.start);
            }
            if (hierarchy.buildingId != null) {
                recomputeBucket(orgId, DimensionType.BUILDING, hierarchy.buildingId, bucket.granularity, bucket.start);
            }
            recomputeBucket(orgId, DimensionType.SITE, hierarchy.siteId, bucket.granularity, bucket.start);
            recomputeBucket(orgId, DimensionType.ORGANIZATION, orgId, bucket.granularity, bucket.start);
        }
        return buckets.size();
    }

    private MeterHierarchy meterHierarchy(UUID organizationId, UUID meterId) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT m.site_id, m.building_id, m.zone_id
                FROM telemetry.meter m
                WHERE m.id = :meterId AND m.organization_id = :orgId
                """).setParameter("meterId", meterId).setParameter("orgId", organizationId).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = (Object[]) rows.get(0);
        return new MeterHierarchy(toUuid(row[0]), toUuid(row[1]), toUuid(row[2]));
    }

    private UUID organizationIdForMeter(UUID meterId) {
        List<?> rows = entityManager.createNativeQuery(
                "SELECT organization_id FROM telemetry.meter WHERE id = :meterId")
                .setParameter("meterId", meterId).getResultList();
        return rows.isEmpty() ? null : toUuid(rows.get(0));
    }

    private static UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u;
        }
        if (value instanceof byte[] bytes) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(value.toString());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        throw new IllegalArgumentException("Unsupported timestamp type: " + value.getClass());
    }

    private record MeterHierarchy(UUID siteId, UUID buildingId, UUID zoneId) {
    }

    private record Bucket(AggregationGranularity granularity, Instant start) {
    }
}
