package com.enerlytics.forecast.provider;

import com.enerlytics.forecast.config.ForecastProperties;
import com.enerlytics.forecast.domain.ForecastHorizon;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Deterministic exact-decimal math shared by the statistical forecast
 * providers. All computations use {@link MathContext#DECIMAL128}-style
 * precision and {@link RoundingMode#HALF_EVEN} so results are stable across
 * runs and platforms.
 */
final class ForecastMath {

    static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    static final int SCALE = 9;
    static final BigDecimal SECONDS_PER_DAY = new BigDecimal("86400");
    static final BigDecimal HUNDRED = new BigDecimal("100");

    private ForecastMath() {
    }

    static BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v, MC);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    /** Population standard deviation over the values. */
    static BigDecimal stddev(List<BigDecimal> values, BigDecimal mean) {
        BigDecimal sumSq = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            BigDecimal d = v.subtract(mean, MC);
            sumSq = sumSq.add(d.multiply(d, MC), MC);
        }
        return sumSq.divide(BigDecimal.valueOf(values.size()), MC).sqrt(MC);
    }

    /**
     * Least-squares slope of {@code y[i]} regressed on {@code x[i] = i}
     * ({@code i = 0..n-1}). Requires at least two points and a non-constant
     * index axis, which is guaranteed for {@code n >= 2}.
     */
    static BigDecimal linearSlope(List<BigDecimal> y) {
        BigDecimal n = BigDecimal.valueOf(y.size());
        BigDecimal sumX = n.multiply(n.subtract(BigDecimal.ONE, MC), MC)
                .divide(BigDecimal.valueOf(2), MC);
        BigDecimal sumY = BigDecimal.ZERO;
        BigDecimal sumXY = BigDecimal.ZERO;
        for (int i = 0; i < y.size(); i++) {
            sumY = sumY.add(y.get(i), MC);
            sumXY = sumXY.add(BigDecimal.valueOf(i).multiply(y.get(i), MC), MC);
        }
        BigDecimal sumX2 = n.subtract(BigDecimal.ONE, MC).multiply(n, MC)
                .multiply(n.multiply(BigDecimal.valueOf(2), MC).subtract(BigDecimal.ONE, MC), MC)
                .divide(BigDecimal.valueOf(6), MC);
        BigDecimal numerator = n.multiply(sumXY, MC).subtract(sumX.multiply(sumY, MC), MC);
        BigDecimal denominator = n.multiply(sumX2, MC).subtract(sumX.multiply(sumX, MC), MC);
        return numerator.divide(denominator, MC);
    }

    /**
     * Seasonal position of a bucket start within its seasonal cycle:
     * hour-of-week (0–167) for hourly horizons, day-of-week (0–6) for daily
     * horizons. Boundaries are UTC, matching the aggregate buckets.
     */
    static int seasonalIndex(Instant bucketStart, ForecastHorizon horizon) {
        ZonedDateTime z = ZonedDateTime.ofInstant(bucketStart, ZoneOffset.UTC);
        return switch (horizon) {
            case NEXT_24_HOURS -> (z.getDayOfWeek().getValue() - 1) * 24 + z.getHour();
            case NEXT_7_DAYS -> z.getDayOfWeek().getValue() - 1;
        };
    }

    /** Hour-of-day (0–23), used by the same-hour baseline. */
    static int hourOfDay(Instant bucketStart) {
        return ZonedDateTime.ofInstant(bucketStart, ZoneOffset.UTC).getHour();
    }

    /** Decimal days between the end of history and a forecast bucket start. */
    static BigDecimal daysAhead(Instant historyEnd, Instant bucketStart) {
        long seconds = bucketStart.getEpochSecond() - historyEnd.getEpochSecond();
        return BigDecimal.valueOf(seconds).divide(SECONDS_PER_DAY, MC);
    }

    /** Prediction interval: predicted ± z×sigma, clamped to >= 0. */
    static BigDecimal[] bounds(BigDecimal predicted, BigDecimal sigma, ForecastProperties props) {
        BigDecimal margin;
        if (sigma != null) {
            margin = sigma.multiply(props.getIntervalZ(), MC);
        } else {
            margin = predicted.abs().multiply(props.getFallbackMarginPct(), MC)
                    .divide(HUNDRED, MC);
        }
        BigDecimal lower = predicted.subtract(margin, MC).max(BigDecimal.ZERO);
        BigDecimal upper = predicted.add(margin, MC);
        return new BigDecimal[]{lower, upper};
    }

    static BigDecimal scale(BigDecimal v) {
        return v.setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    static BigDecimal scale6(BigDecimal v) {
        return v.setScale(6, RoundingMode.HALF_EVEN);
    }
}
