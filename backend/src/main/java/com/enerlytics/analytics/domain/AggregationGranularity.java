package com.enerlytics.analytics.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Time buckets for energy aggregation. Day and month boundaries are UTC;
 * site-local reporting boundaries are a documented future enhancement.
 */
public enum AggregationGranularity {
    QUARTER_HOUR,
    HOUR,
    DAY,
    MONTH;

    public Instant bucketStart(Instant timestamp) {
        return switch (this) {
            case QUARTER_HOUR -> Instant.ofEpochSecond(timestamp.getEpochSecond() / 900 * 900);
            case HOUR -> Instant.ofEpochSecond(timestamp.getEpochSecond() / 3600 * 3600);
            case DAY -> ZonedDateTime.ofInstant(timestamp, ZoneOffset.UTC).toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
            case MONTH -> LocalDate.ofInstant(timestamp, ZoneOffset.UTC).withDayOfMonth(1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
        };
    }

    public Instant bucketEnd(Instant bucketStart) {
        return switch (this) {
            case QUARTER_HOUR -> bucketStart.plusSeconds(900);
            case HOUR -> bucketStart.plusSeconds(3600);
            case DAY -> bucketStart.plusSeconds(86400);
            case MONTH -> LocalDate.ofInstant(bucketStart, ZoneOffset.UTC).plusMonths(1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
        };
    }
}
