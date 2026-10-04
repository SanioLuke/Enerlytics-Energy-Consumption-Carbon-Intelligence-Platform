package com.enerlytics.forecast.api.dto;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.forecast.domain.ForecastHorizon;
import com.enerlytics.forecast.domain.ForecastMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ForecastRunSummaryResponse(
        UUID runId,
        DimensionType entityType,
        UUID entityId,
        ForecastHorizon horizon,
        ForecastMethod method,
        Instant generatedAt,
        BigDecimal maeKwh,
        BigDecimal mapePct,
        Integer evaluatedPoints,
        Instant evaluatedAt) {
}
