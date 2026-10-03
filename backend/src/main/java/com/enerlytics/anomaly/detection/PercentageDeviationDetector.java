package com.enerlytics.anomaly.detection;

import com.enerlytics.anomaly.config.AnomalyDetectionProperties;
import com.enerlytics.anomaly.domain.AnomalyMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Configurable percentage deviation: flags hours whose consumption deviates
 * from the same hour on the previous day by more than the configured threshold.
 */
@Component
public class PercentageDeviationDetector implements AnomalyDetector {

    private static final Duration DAY = Duration.ofDays(1);

    @Override
    public AnomalyMethod method() {
        return AnomalyMethod.PERCENTAGE_DEVIATION;
    }

    @Override
    public List<AnomalyCandidate> detect(List<HourlyPoint> series, AnomalyDetectionProperties props) {
        TreeMap<java.time.Instant, BigDecimal> byStart = new TreeMap<>();
        for (HourlyPoint p : series) {
            byStart.put(p.bucketStart(), p.energyKwh());
        }
        List<AnomalyCandidate> out = new ArrayList<>();
        for (HourlyPoint point : series) {
            BigDecimal previous = byStart.get(point.bucketStart().minus(DAY));
            if (previous == null || DetectorMath.belowExpectedFloor(previous, props)) {
                continue;
            }
            if (DetectorMath.exceedsThreshold(point.energyKwh(), previous,
                    props.getPercentageThresholdPct(), props)) {
                out.add(DetectorMath.candidate(point, previous, method(),
                        props.getPercentageThresholdPct(), "previous-day same hour"));
            }
        }
        return out;
    }
}
