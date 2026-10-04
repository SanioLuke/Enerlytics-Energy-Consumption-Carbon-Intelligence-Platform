package com.enerlytics.forecast.provider;

import com.enerlytics.forecast.domain.ForecastMethod;

import java.util.List;

/**
 * Strategy interface for producing energy-consumption forecasts.
 *
 * <p>Implementations must be deterministic: the same {@link ForecastContext}
 * must always produce the same predictions. A future ML-backed service can be
 * introduced by registering another bean returning
 * {@code ForecastMethod.EXTERNAL_MODEL}; orchestration, persistence, and API
 * layers do not change.</p>
 */
public interface ForecastProvider {

    ForecastMethod method();

    /**
     * Produces exactly {@code context.horizon().getBucketCount()} predictions
     * starting at {@code context.firstBucketStart()} and stepping by the
     * horizon's bucket size.
     */
    List<ForecastPrediction> forecast(ForecastContext context);
}
