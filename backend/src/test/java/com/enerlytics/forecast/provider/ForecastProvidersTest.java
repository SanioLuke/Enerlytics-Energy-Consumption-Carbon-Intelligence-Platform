package com.enerlytics.forecast.provider;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.forecast.config.ForecastProperties;
import com.enerlytics.forecast.domain.ForecastHorizon;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic time-series tests for the built-in statistical forecast
 * providers. Every expectation is an exact decimal result of the documented
 * formula — no approximate assertions.
 */
class ForecastProvidersTest {

    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final Instant MONDAY = Instant.parse("2026-01-05T00:00:00Z");
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID METER = UUID.randomUUID();

    private final ForecastProperties props = new ForecastProperties();

    @Test
    void seasonalMovingAveragePredictsHourOfWeekMeans() {
        // Two full weeks; the bucket at hour-of-week h always carries 100 + h kWh.
        Instant first = Instant.parse("2026-01-19T00:00:00Z"); // Monday
        List<ForecastSample> history = hourly(14,
                (d, h) -> new BigDecimal(100 + (d * 24 + h) % 168));

        List<ForecastPrediction> out = new SeasonalMovingAverageProvider()
                .forecast(ctx(ForecastHorizon.NEXT_24_HOURS, first, history));

        assertThat(out).hasSize(24);
        for (int i = 0; i < 24; i++) {
            ForecastPrediction p = out.get(i);
            BigDecimal expected = new BigDecimal(100 + i); // hour-of-week i
            assertThat(p.timestamp()).isEqualTo(first.plus(i, ChronoUnit.HOURS));
            assertThat(p.predictedKwh()).isEqualByComparingTo(expected);
            // Identical samples -> sigma = 0 -> degenerate interval.
            assertThat(p.lowerBoundKwh()).isEqualByComparingTo(expected);
            assertThat(p.upperBoundKwh()).isEqualByComparingTo(expected);
        }
    }

    @Test
    void sameHourBaselinePredictsHourOfDayMeans() {
        // 14 days; hour-of-day h always carries 10 + h kWh.
        Instant first = Instant.parse("2026-01-19T05:00:00Z");
        List<ForecastSample> history = hourly(14, (d, h) -> new BigDecimal(10 + h));

        List<ForecastPrediction> out = new SameHourBaselineProvider()
                .forecast(ctx(ForecastHorizon.NEXT_24_HOURS, first, history));

        assertThat(out).hasSize(24);
        for (int i = 0; i < 24; i++) {
            int hour = (5 + i) % 24;
            assertThat(out.get(i).predictedKwh())
                    .isEqualByComparingTo(new BigDecimal(10 + hour));
        }
    }

    @Test
    void trendAdjustedAddsDailySlopeToSeasonalMean() {
        // 14 days; every hour of day d carries 10 + d kWh.
        // Daily totals = 24*(10 + d) -> exact least-squares slope = 24 kWh/day.
        Instant first = Instant.parse("2026-01-19T00:00:00Z"); // Monday
        List<ForecastSample> history = hourly(14, (d, h) -> new BigDecimal(10 + d));

        List<ForecastPrediction> out = new TrendAdjustedProvider()
                .forecast(ctx(ForecastHorizon.NEXT_24_HOURS, first, history));

        // Bucket 0 (Mon 00:00): hour-of-week 0 samples 10 and 17 -> 13.5, 0 days ahead.
        assertThat(out.get(0).predictedKwh()).isEqualByComparingTo("13.500000000");
        // Bucket 23 (Mon 23:00): hour-of-week 23 samples 10 and 17 -> 13.5
        // + 24 kWh/day * (23/24) days = 13.5 + 23 = 36.5.
        assertThat(out.get(23).predictedKwh()).isEqualByComparingTo("36.500000000");
    }

    @Test
    void underSampledSeasonalPositionFallsBackToOverallMean() {
        // 14 days at 10 + h per hour, with all but one hour-5 sample removed.
        Instant first = Instant.parse("2026-01-19T05:00:00Z");
        List<ForecastSample> history = new ArrayList<>();
        for (int d = 0; d < 14; d++) {
            for (int h = 0; h < 24; h++) {
                if (h == 5 && d > 0) {
                    continue; // one hour-5 sample left -> falls back
                }
                history.add(new ForecastSample(MONDAY.plus(d * 24L + h, ChronoUnit.HOURS),
                        new BigDecimal(10 + h)));
            }
        }
        BigDecimal sum = history.stream().map(ForecastSample::kwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = sum.divide(BigDecimal.valueOf(history.size()), MC)
                .setScale(9, RoundingMode.HALF_EVEN);

        List<ForecastPrediction> out = new SeasonalMovingAverageProvider()
                .forecast(ctx(ForecastHorizon.NEXT_24_HOURS, first, history));

        // Bucket 0 targets Monday 05:00 -> hour-of-week 5 -> single sample.
        assertThat(out.get(0).predictedKwh()).isEqualByComparingTo(expected);
    }

    @Test
    void seasonalMovingAverageForDailyHorizonUsesDayOfWeek() {
        // 14 DAY buckets; value = dayOfWeekValue * 10 (Mon=10 ... Sun=70).
        Instant first = Instant.parse("2026-01-19T00:00:00Z"); // Monday
        List<ForecastSample> history = new ArrayList<>();
        for (int d = 0; d < 14; d++) {
            Instant day = MONDAY.plus(d, ChronoUnit.DAYS);
            int dow = day.atOffset(ZoneOffset.UTC).getDayOfWeek().getValue();
            history.add(new ForecastSample(day, new BigDecimal(dow * 10)));
        }

        List<ForecastPrediction> out = new SeasonalMovingAverageProvider()
                .forecast(ctx(ForecastHorizon.NEXT_7_DAYS, first, history));

        assertThat(out).hasSize(7);
        // Buckets Mon..Sun -> 10,20,...,70 exactly (two identical samples each).
        for (int i = 0; i < 7; i++) {
            assertThat(out.get(i).predictedKwh())
                    .isEqualByComparingTo(new BigDecimal((i + 1) * 10));
            assertThat(out.get(i).lowerBoundKwh()).isEqualByComparingTo(out.get(i).predictedKwh());
            assertThat(out.get(i).upperBoundKwh()).isEqualByComparingTo(out.get(i).predictedKwh());
        }
    }

    @Test
    void predictionIntervalWidensWithSampleSpread() {
        // Same hour-of-week position alternates 90 and 110 -> mean 100, sigma 10.
        Instant first = Instant.parse("2026-01-19T00:00:00Z");
        List<ForecastSample> history = hourly(14,
                (d, h) -> new BigDecimal(d % 2 == 0 ? 90 : 110));

        List<ForecastPrediction> out = new SeasonalMovingAverageProvider()
                .forecast(ctx(ForecastHorizon.NEXT_24_HOURS, first, history));

        // sigma = 10, z = 1.96 -> margin = 19.6; bounds 80.4 and 119.6.
        assertThat(out.get(0).predictedKwh()).isEqualByComparingTo("100.000000000");
        assertThat(out.get(0).lowerBoundKwh()).isEqualByComparingTo("80.400000000");
        assertThat(out.get(0).upperBoundKwh()).isEqualByComparingTo("119.600000000");
    }

    private ForecastContext ctx(ForecastHorizon horizon, Instant first,
                                List<ForecastSample> history) {
        return new ForecastContext(ORG, DimensionType.METER, METER, horizon,
                first, history, props);
    }

    private List<ForecastSample> hourly(int days,
                                        BiFunction<Integer, Integer, BigDecimal> value) {
        List<ForecastSample> history = new ArrayList<>(days * 24);
        for (int d = 0; d < days; d++) {
            for (int h = 0; h < 24; h++) {
                history.add(new ForecastSample(MONDAY.plus(d * 24L + h, ChronoUnit.HOURS),
                        value.apply(d, h)));
            }
        }
        return history;
    }
}
