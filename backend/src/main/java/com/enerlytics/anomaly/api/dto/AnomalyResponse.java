package com.enerlytics.anomaly.api.dto;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.anomaly.domain.AnomalyMethod;
import com.enerlytics.anomaly.domain.AnomalySeverity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AnomalyResponse(
        UUID id,
        DimensionType entityType,
        UUID entityId,
        Instant timestamp,
        BigDecimal actualConsumptionKwh,
        BigDecimal expectedConsumptionKwh,
        BigDecimal deviationPercentage,
        AnomalyMethod detectionMethod,
        BigDecimal confidence,
        AnomalySeverity severity,
        String explanation,
        Instant detectedAt) {
}
