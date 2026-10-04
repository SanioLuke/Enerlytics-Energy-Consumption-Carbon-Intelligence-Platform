package com.enerlytics.forecast.provider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One observed history bucket passed to a {@link ForecastProvider}.
 * Buckets use the same granularity as the requested forecast horizon.
 */
public record ForecastSample(Instant bucketStart, BigDecimal kwh) {
}
