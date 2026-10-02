package com.enerlytics.billing.api.dto;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CostBucketResponse(
        UUID organizationId,
        DimensionType dimension,
        UUID dimensionId,
        AggregationGranularity granularity,
        Instant bucketStart,
        Instant bucketEnd,
        String currency,
        BigDecimal energyConsumedKwh,
        BigDecimal energyCost,
        BigDecimal demandCharge,
        BigDecimal totalCost,
        BigDecimal coverageRatio,
        String qualityStatus,
        int missingRateHours) {
}
