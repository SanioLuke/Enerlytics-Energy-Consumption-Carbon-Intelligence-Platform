package com.enerlytics.carbon.api.dto;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Canonical emission result for a scope, dimension, and time bucket.
 */
public record CarbonEmissionResponse(
        UUID organizationId,
        DimensionType dimension,
        UUID dimensionId,
        AggregationGranularity granularity,
        Instant bucketStart,
        Instant bucketEnd,
        String gridRegionCode,
        BigDecimal energyConsumedKwh,
        BigDecimal carbonIntensityGCo2EqPerKwh,
        String carbonIntensitySource,
        BigDecimal emissionsGCo2Eq,
        BigDecimal emissionsKgCo2Eq,
        BigDecimal emissionsTCo2Eq,
        BigDecimal coverageRatio,
        boolean estimated,
        String qualityStatus) {
}
