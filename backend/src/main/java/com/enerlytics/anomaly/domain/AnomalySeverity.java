package com.enerlytics.anomaly.domain;

import java.math.BigDecimal;

public enum AnomalySeverity {
    LOW, MEDIUM, HIGH, CRITICAL;

    /** Deterministic severity banding on absolute deviation percentage. */
    public static AnomalySeverity fromDeviationPct(BigDecimal deviationPct) {
        BigDecimal abs = deviationPct.abs();
        if (abs.compareTo(new BigDecimal("500")) >= 0) {
            return CRITICAL;
        }
        if (abs.compareTo(new BigDecimal("250")) >= 0) {
            return HIGH;
        }
        if (abs.compareTo(new BigDecimal("100")) >= 0) {
            return MEDIUM;
        }
        return LOW;
    }
}
