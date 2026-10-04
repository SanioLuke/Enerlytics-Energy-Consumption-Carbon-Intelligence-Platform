package com.enerlytics.forecast.provider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One predicted bucket produced by a {@link ForecastProvider}.
 */
public record ForecastPrediction(
        Instant timestamp,
        BigDecimal predictedKwh,
        BigDecimal lowerBoundKwh,
        BigDecimal upperBoundKwh,
        String explanation) {
}
