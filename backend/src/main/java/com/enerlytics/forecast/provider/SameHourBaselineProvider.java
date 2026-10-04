package com.enerlytics.forecast.provider;

import com.enerlytics.forecast.domain.ForecastMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Same-hour historical baseline: for {@code NEXT_24_HOURS} each bucket is the
 * mean of the same UTC hour-of-day across the history window. For
 * {@code NEXT_7_DAYS} (daily buckets, no intra-day position) the baseline is
 * the mean daily total across the window.
 */
@Component
public class SameHourBaselineProvider extends BaseStatisticalProvider {

    @Override
    public ForecastMethod method() {
        return ForecastMethod.SAME_HOUR_BASELINE;
    }

    @Override
    protected int positionIndex(ForecastContext context, Instant bucketStart) {
        return switch (context.horizon()) {
            case NEXT_24_HOURS -> ForecastMath.hourOfDay(bucketStart);
            case NEXT_7_DAYS -> 0;
        };
    }

    @Override
    protected String explain(ForecastContext context, Instant target,
                             List<BigDecimal> samples, BigDecimal predicted, Instant historyEnd) {
        String position = switch (context.horizon()) {
            case NEXT_24_HOURS -> "same-hour";
            case NEXT_7_DAYS -> "daily";
        };
        return "Mean of " + samples.size() + " " + position + " baseline samples = "
                + ForecastMath.scale6(predicted).toPlainString() + " kWh";
    }
}
