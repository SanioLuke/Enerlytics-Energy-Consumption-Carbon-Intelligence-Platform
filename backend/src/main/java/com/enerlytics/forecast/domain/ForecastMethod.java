package com.enerlytics.forecast.domain;

/**
 * Forecast method identifying which {@code ForecastProvider} produced a run.
 * {@code EXTERNAL_MODEL} is reserved for a future ML-backed provider.
 */
public enum ForecastMethod {
    SEASONAL_MOVING_AVERAGE,
    SAME_HOUR_BASELINE,
    TREND_ADJUSTED,
    EXTERNAL_MODEL
}
