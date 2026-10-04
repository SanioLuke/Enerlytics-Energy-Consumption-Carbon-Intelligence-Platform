package com.enerlytics.forecast.domain;

import com.enerlytics.analytics.domain.AggregationGranularity;

import java.time.Duration;
import java.time.Instant;

/**
 * Supported forecast horizons. Each horizon fixes the bucket granularity,
 * bucket count, and how the first forecast bucket is derived from a
 * reference instant.
 */
public enum ForecastHorizon {
    NEXT_24_HOURS(AggregationGranularity.HOUR, 24, Duration.ofHours(1)),
    NEXT_7_DAYS(AggregationGranularity.DAY, 7, Duration.ofDays(1));

    private final AggregationGranularity granularity;
    private final int bucketCount;
    private final Duration bucketSize;

    ForecastHorizon(AggregationGranularity granularity, int bucketCount, Duration bucketSize) {
        this.granularity = granularity;
        this.bucketCount = bucketCount;
        this.bucketSize = bucketSize;
    }

    public AggregationGranularity getGranularity() {
        return granularity;
    }

    public int getBucketCount() {
        return bucketCount;
    }

    public Duration getBucketSize() {
        return bucketSize;
    }

    /**
     * The first forecast bucket is the complete bucket that begins immediately
     * after the bucket containing {@code asOf} ends.
     */
    public Instant firstBucketStart(Instant asOf) {
        return granularity.bucketEnd(granularity.bucketStart(asOf));
    }
}
