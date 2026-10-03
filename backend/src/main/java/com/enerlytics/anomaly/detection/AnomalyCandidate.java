package com.enerlytics.anomaly.detection;

import com.enerlytics.anomaly.domain.AnomalyMethod;
import com.enerlytics.anomaly.domain.AnomalySeverity;

import java.math.BigDecimal;
import java.time.Instant;

/** A detection produced by an {@link AnomalyDetector} before suppression and persistence. */
public record AnomalyCandidate(
        Instant bucketStart,
        BigDecimal actualKwh,
        BigDecimal expectedKwh,
        BigDecimal deviationPct,
        AnomalyMethod method,
        AnomalySeverity severity,
        BigDecimal confidence,
        String explanation) {
}
