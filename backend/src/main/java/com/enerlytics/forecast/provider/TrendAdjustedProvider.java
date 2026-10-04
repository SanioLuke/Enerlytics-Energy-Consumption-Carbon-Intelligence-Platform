package com.enerlytics.forecast.provider;

import com.enerlytics.forecast.domain.ForecastMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seasonal baseline adjusted by a deterministic linear trend: the same
 * seasonal position mean plus {@code slope * daysAhead}, where the slope is a
 * least-squares fit (kWh per day) over daily totals in the configured trend
 * window and {@code daysAhead} is measured from the end of the history.
 */
@Component
public class TrendAdjustedProvider extends BaseStatisticalProvider {

    @Override
    public ForecastMethod method() {
        return ForecastMethod.TREND_ADJUSTED;
    }

    @Override
    protected int positionIndex(ForecastContext context, Instant bucketStart) {
        return ForecastMath.seasonalIndex(bucketStart, context.horizon());
    }

    @Override
    protected BigDecimal baseline(ForecastContext context, Instant target,
                                  List<BigDecimal> samples, Instant historyEnd) {
        BigDecimal base = ForecastMath.mean(samples);
        BigDecimal slope = dailySlope(context);
        BigDecimal daysAhead = ForecastMath.daysAhead(historyEnd, target);
        return base.add(slope.multiply(daysAhead, ForecastMath.MC), ForecastMath.MC);
    }

    /**
     * Least-squares slope (kWh per day) on the tail of the history. For
     * hourly history, values are summed into UTC daily totals first so the
     * slope is expressed per day in both horizons.
     */
    private BigDecimal dailySlope(ForecastContext context) {
        Map<LocalDate, BigDecimal> dailyTotals = new LinkedHashMap<>();
        for (ForecastSample s : context.history()) {
            LocalDate day = ZonedDateTime.ofInstant(s.bucketStart(), ZoneOffset.UTC).toLocalDate();
            dailyTotals.merge(day, s.kwh(), BigDecimal::add);
        }
        List<BigDecimal> totals = new ArrayList<>(dailyTotals.values());
        int n = Math.min(totals.size(), context.properties().getTrendLookbackDays());
        totals = totals.subList(totals.size() - n, totals.size());
        if (totals.size() < 2) {
            return BigDecimal.ZERO;
        }
        return ForecastMath.linearSlope(totals);
    }

    @Override
    protected String explain(ForecastContext context, Instant target,
                             List<BigDecimal> samples, BigDecimal predicted, Instant historyEnd) {
        BigDecimal base = ForecastMath.mean(samples);
        BigDecimal slope = dailySlope(context);
        BigDecimal daysAhead = ForecastMath.daysAhead(historyEnd, target);
        return "Seasonal mean " + ForecastMath.scale6(base).toPlainString()
                + " kWh + trend " + slope.setScale(6, RoundingMode.HALF_EVEN).toPlainString()
                + " kWh/day × " + daysAhead.setScale(6, RoundingMode.HALF_EVEN).toPlainString()
                + " days = " + ForecastMath.scale6(predicted).toPlainString() + " kWh";
    }
}
