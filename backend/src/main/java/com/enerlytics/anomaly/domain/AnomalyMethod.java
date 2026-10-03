package com.enerlytics.anomaly.domain;

public enum AnomalyMethod {
    ROLLING_MEAN_DEVIATION,
    ROLLING_ZSCORE,
    SAME_HOUR_BASELINE,
    PERCENTAGE_DEVIATION
}
