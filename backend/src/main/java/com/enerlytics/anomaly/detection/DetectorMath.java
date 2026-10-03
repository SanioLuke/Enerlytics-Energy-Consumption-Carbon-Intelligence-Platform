package com.enerlytics.anomaly.detection;

import com.enerlytics.anomaly.domain.AnomalyMethod;
import com.enerlytics.anomaly.domain.AnomalySeverity;
import com.enerlytics.anomaly.config.AnomalyDetectionProperties;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

final class DetectorMath {

    static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    static final BigDecimal HUNDRED = new BigDecimal("100");
    static final BigDecimal CONFIDENCE_CAP = new BigDecimal("0.99");

    private DetectorMath() {
    }

    static BigDecimal mean(List<HourlyPoint> window) {
        BigDecimal sum = BigDecimal.ZERO;
        for (HourlyPoint p : window) {
            sum = sum.add(p.energyKwh(), MC);
        }
        return sum.divide(BigDecimal.valueOf(window.size()), MC);
    }

    /** Population standard deviation over the window. */
    static BigDecimal stddev(List<HourlyPoint> window, BigDecimal mean) {
        BigDecimal sumSq = BigDecimal.ZERO;
        for (HourlyPoint p : window) {
            BigDecimal d = p.energyKwh().subtract(mean, MC);
            sumSq = sumSq.add(d.multiply(d, MC), MC);
        }
        return sumSq.divide(BigDecimal.valueOf(window.size()), MC).sqrt(MC);
    }

    static BigDecimal deviationPct(BigDecimal actual, BigDecimal expected) {
        return actual.subtract(expected, MC)
                .divide(expected, MC)
                .multiply(HUNDRED, MC);
    }

    /** min(0.99, devAbs / (2 * threshold)) — deterministic 0.5 at threshold. */
    static BigDecimal confidence(BigDecimal deviationAbsPct, BigDecimal thresholdPct) {
        BigDecimal c = deviationAbsPct.divide(thresholdPct.multiply(BigDecimal.valueOf(2), MC), MC);
        return c.min(CONFIDENCE_CAP).setScale(4, RoundingMode.HALF_EVEN);
    }

    static boolean belowExpectedFloor(BigDecimal expected, AnomalyDetectionProperties props) {
        return expected.compareTo(props.getMinExpectedKwh()) < 0;
    }

    static AnomalyCandidate candidate(HourlyPoint point, BigDecimal expected, AnomalyMethod method,
                                    BigDecimal thresholdPct, String expectedLabel) {
        BigDecimal dev = deviationPct(point.energyKwh(), expected);
        BigDecimal devPct = dev.setScale(6, RoundingMode.HALF_EVEN);
        String direction = dev.signum() >= 0 ? "above" : "below";
        String explanation = "Actual " + point.energyKwh().setScale(6, RoundingMode.HALF_EVEN).toPlainString()
                + " kWh is " + devPct.abs().toPlainString() + "% " + direction + " "
                + expectedLabel + " of " + expected.setScale(6, RoundingMode.HALF_EVEN).toPlainString()
                + " kWh";
        return new AnomalyCandidate(point.bucketStart(), point.energyKwh(),
                expected.setScale(9, RoundingMode.HALF_EVEN), devPct, method,
                AnomalySeverity.fromDeviationPct(devPct),
                confidence(devPct.abs(), thresholdPct), explanation);
    }

    static boolean exceedsThreshold(BigDecimal actual, BigDecimal expected, BigDecimal thresholdPct,
                                    AnomalyDetectionProperties props) {
        if (belowExpectedFloor(expected, props)) {
            return false;
        }
        return deviationPct(actual, expected).abs().compareTo(thresholdPct) > 0;
    }

    static List<HourlyPoint> subList(List<HourlyPoint> series, int from, int toExclusive) {
        return series.subList(Math.max(0, from), toExclusive);
    }

    static boolean isContiguous(List<HourlyPoint> window, HourlyPoint current) {
        if (window.isEmpty()) {
            return false;
        }
        for (int i = 1; i < window.size(); i++) {
            if (!Duration.between(window.get(i - 1).bucketStart(), window.get(i).bucketStart())
                    .equals(Duration.ofHours(1))) {
                return false;
            }
        }
        return Duration.between(window.get(window.size() - 1).bucketStart(), current.bucketStart())
                .equals(Duration.ofHours(1));
    }
}
