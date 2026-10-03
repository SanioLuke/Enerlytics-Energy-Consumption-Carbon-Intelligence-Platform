package com.enerlytics.anomaly.detection;

import com.enerlytics.anomaly.config.AnomalyDetectionProperties;
import com.enerlytics.anomaly.domain.AnomalyMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterpretableDetectorsTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private AnomalyDetectionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AnomalyDetectionProperties();
        properties.setRollingWindowHours(6);
        properties.setRollingMeanThresholdPct(new BigDecimal("50"));
        properties.setZscoreThreshold(new BigDecimal("3"));
        properties.setSameHourLookbackDays(7);
        properties.setSameHourMinSamples(5);
        properties.setSameHourThresholdPct(new BigDecimal("60"));
        properties.setPercentageThresholdPct(new BigDecimal("75"));
    }

    @Test
    void rollingMeanFindsKnownSpikeWithExactExpectedValue() {
        List<HourlyPoint> series = constantHourlySeries(7, "10.000");
        series.set(6, point(6, "30.000", "100"));

        List<AnomalyCandidate> result = new RollingMeanDeviationDetector().detect(series, properties);

        assertThat(result).hasSize(1);
        AnomalyCandidate anomaly = result.get(0);
        assertThat(anomaly.method()).isEqualTo(AnomalyMethod.ROLLING_MEAN_DEVIATION);
        assertThat(anomaly.actualKwh()).isEqualByComparingTo("30.000");
        assertThat(anomaly.expectedKwh()).isEqualByComparingTo("10.000000000");
        assertThat(anomaly.deviationPct()).isEqualByComparingTo("200.000000");
        assertThat(anomaly.explanation()).contains("trailing 6h mean");
    }

    @Test
    void rollingZScoreFindsKnownSpike() {
        List<HourlyPoint> series = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            series.add(point(i, i % 2 == 0 ? "9.000" : "11.000", "100"));
        }
        series.add(point(6, "20.000", "100"));

        List<AnomalyCandidate> result = new RollingZScoreDetector().detect(series, properties);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).method()).isEqualTo(AnomalyMethod.ROLLING_ZSCORE);
        assertThat(result.get(0).expectedKwh()).isEqualByComparingTo("10.000000000");
        assertThat(result.get(0).deviationPct()).isEqualByComparingTo("100.000000");
        assertThat(result.get(0).explanation()).contains("z-score");
    }

    @Test
    void sameHourBaselineUsesPriorDailySamples() {
        List<HourlyPoint> series = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            series.add(new HourlyPoint(START.plus(day, ChronoUnit.DAYS),
                    new BigDecimal(day == 6 ? "25.000" : "10.000"), new BigDecimal("100")));
        }

        List<AnomalyCandidate> result = new SameHourBaselineDetector().detect(series, properties);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).method()).isEqualTo(AnomalyMethod.SAME_HOUR_BASELINE);
        assertThat(result.get(0).expectedKwh()).isEqualByComparingTo("10.000000000");
        assertThat(result.get(0).deviationPct()).isEqualByComparingTo("150.000000");
        assertThat(result.get(0).explanation()).contains("6-day same-hour baseline");
    }

    @Test
    void configurablePercentageDeviationUsesPreviousDay() {
        List<HourlyPoint> series = List.of(
                new HourlyPoint(START, new BigDecimal("8.000"), new BigDecimal("100")),
                new HourlyPoint(START.plus(1, ChronoUnit.DAYS), new BigDecimal("20.000"), new BigDecimal("100")));

        List<AnomalyCandidate> result = new PercentageDeviationDetector().detect(series, properties);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).method()).isEqualTo(AnomalyMethod.PERCENTAGE_DEVIATION);
        assertThat(result.get(0).expectedKwh()).isEqualByComparingTo("8.000000000");
        assertThat(result.get(0).deviationPct()).isEqualByComparingTo("150.000000");
    }

    @Test
    void rollingDetectorsDoNotBridgeMissingTelemetryGap() {
        List<HourlyPoint> series = constantHourlySeries(6, "10.000");
        series.set(5, point(7, "10.000", "100"));
        series.add(point(8, "30.000", "100"));

        assertThat(new RollingMeanDeviationDetector().detect(series, properties)).isEmpty();
        assertThat(new RollingZScoreDetector().detect(series, properties)).isEmpty();
    }

    @Test
    void sameHourDetectorRequiresMinimumBaselineSamples() {
        List<HourlyPoint> series = List.of(
                new HourlyPoint(START, new BigDecimal("10"), new BigDecimal("100")),
                new HourlyPoint(START.plus(1, ChronoUnit.DAYS), new BigDecimal("10"), new BigDecimal("100")),
                new HourlyPoint(START.plus(2, ChronoUnit.DAYS), new BigDecimal("100"), new BigDecimal("100")));

        assertThat(new SameHourBaselineDetector().detect(series, properties)).isEmpty();
    }

    private List<HourlyPoint> constantHourlySeries(int count, String energy) {
        List<HourlyPoint> series = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            series.add(point(i, energy, "100"));
        }
        return series;
    }

    private HourlyPoint point(int hour, String energy, String completeness) {
        return new HourlyPoint(START.plus(hour, ChronoUnit.HOURS),
                new BigDecimal(energy), new BigDecimal(completeness));
    }
}
