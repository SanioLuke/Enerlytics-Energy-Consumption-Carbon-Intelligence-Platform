package com.enerlytics.forecast.provider;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.forecast.config.ForecastProperties;
import com.enerlytics.forecast.domain.ForecastHorizon;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable input for a {@link ForecastProvider}. {@code history} is sorted
 * ascending by {@code bucketStart} and uses the horizon's granularity.
 */
public record ForecastContext(
        UUID organizationId,
        DimensionType dimensionType,
        UUID dimensionId,
        ForecastHorizon horizon,
        Instant firstBucketStart,
        List<ForecastSample> history,
        ForecastProperties properties) {
}
