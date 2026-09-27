package com.enerlytics.analytics.api.dto;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnergyAggregateResponse(
        DimensionType dimension,
        UUID dimensionId,
        AggregationGranularity granularity,
        Instant bucketStart,
        Instant bucketEnd,
        BigDecimal energyConsumedKwh,
        BigDecimal averagePowerKw,
        BigDecimal peakPowerKw,
        BigDecimal minimumPowerKw,
        BigDecimal averagePowerFactor,
        long readingCount,
        long estimatedReadingCount,
        BigDecimal dataCompletenessPercentage,
        Instant computedAt) {
}
