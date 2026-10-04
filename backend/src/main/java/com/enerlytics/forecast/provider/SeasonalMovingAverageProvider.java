package com.enerlytics.forecast.provider;

import com.enerlytics.forecast.domain.ForecastMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Seasonal moving average: each forecast bucket is the mean of the same
 * seasonal position (hour-of-week for {@code NEXT_24_HOURS}, day-of-week for
 * {@code NEXT_7_DAYS}) across the loaded history window.
 */
@Component
public class SeasonalMovingAverageProvider extends BaseStatisticalProvider {

    @Override
    public ForecastMethod method() {
        return ForecastMethod.SEASONAL_MOVING_AVERAGE;
    }

    @Override
    protected int positionIndex(ForecastContext context, Instant bucketStart) {
        return ForecastMath.seasonalIndex(bucketStart, context.horizon());
    }

    @Override
    protected String explain(ForecastContext context, Instant target,
                             List<BigDecimal> samples, BigDecimal predicted, Instant historyEnd) {
        String position = switch (context.horizon()) {
            case NEXT_24_HOURS -> "hour-of-week";
            case NEXT_7_DAYS -> "day-of-week";
        };
        return "Mean of " + samples.size() + " " + position + " samples = "
                + ForecastMath.scale6(predicted).toPlainString() + " kWh";
    }
}
