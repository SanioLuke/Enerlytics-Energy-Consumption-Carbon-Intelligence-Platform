package com.enerlytics.forecast.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared deterministic plumbing for the built-in statistical providers:
 * seasonal grouping, per-position mean/stddev, prediction intervals, and the
 * overall-history fallback when a seasonal position is under-sampled.
 */
abstract class BaseStatisticalProvider implements ForecastProvider {

    /** Seasonal position of a bucket under this provider's seasonal model. */
    protected abstract int positionIndex(ForecastContext context, Instant bucketStart);

    @Override
    public List<ForecastPrediction> forecast(ForecastContext context) {
        Map<Integer, List<BigDecimal>> byPosition = new LinkedHashMap<>();
        List<BigDecimal> all = new ArrayList<>(context.history().size());
        for (ForecastSample s : context.history()) {
            byPosition.computeIfAbsent(positionIndex(context, s.bucketStart()), k -> new ArrayList<>())
                    .add(s.kwh());
            all.add(s.kwh());
        }

        int count = context.horizon().getBucketCount();
        Instant historyEnd = context.history().get(context.history().size() - 1).bucketStart()
                .plus(context.horizon().getBucketSize());
        List<ForecastPrediction> predictions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instant target = context.firstBucketStart()
                    .plus(context.horizon().getBucketSize().multipliedBy(i));
            List<BigDecimal> samples = byPosition.getOrDefault(positionIndex(context, target), List.of());
            if (samples.size() < context.properties().getMinSeasonalSamples()) {
                samples = all;
            }
            predictions.add(predict(context, target, samples, historyEnd));
        }
        return predictions;
    }

    /** Baseline prediction for one target bucket from its seasonal samples. */
    protected BigDecimal baseline(ForecastContext context, Instant target,
                                  List<BigDecimal> samples, Instant historyEnd) {
        return ForecastMath.mean(samples);
    }

    private ForecastPrediction predict(ForecastContext context, Instant target,
                                       List<BigDecimal> samples, Instant historyEnd) {
        BigDecimal predicted = baseline(context, target, samples, historyEnd)
                .max(BigDecimal.ZERO);
        BigDecimal sigma = samples.size() >= 2
                ? ForecastMath.stddev(samples, ForecastMath.mean(samples))
                : null;
        BigDecimal[] bounds = ForecastMath.bounds(predicted, sigma, context.properties());
        String explanation = explain(context, target, samples, predicted, historyEnd);
        return new ForecastPrediction(target, ForecastMath.scale(predicted),
                ForecastMath.scale(bounds[0]), ForecastMath.scale(bounds[1]), explanation);
    }

    protected abstract String explain(ForecastContext context, Instant target,
                                      List<BigDecimal> samples, BigDecimal predicted,
                                      Instant historyEnd);
}
