package com.enerlytics.forecast.api.dto;

import com.enerlytics.forecast.domain.ForecastMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record ForecastPointResponse(
        Instant timestamp,
        BigDecimal predictedKwh,
        BigDecimal lowerBoundKwh,
        BigDecimal upperBoundKwh,
        ForecastMethod method,
        Instant generatedAt,
        BigDecimal actualKwh,
        BigDecimal absoluteError,
        BigDecimal pctError,
        String explanation) {
}
