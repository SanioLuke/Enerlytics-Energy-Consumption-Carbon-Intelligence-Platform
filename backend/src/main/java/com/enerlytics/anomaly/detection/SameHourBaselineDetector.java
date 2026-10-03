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
 * Compares each hour with the mean of the same hour-of-day over the prior N
 * days. Requires a minimum sample count so newly-commissioned entities with
 * insufficient baseline never alert.
 */
@Component
public class SameHourBaselineDetector implements AnomalyDetector {

    private static final Duration DAY = Duration.ofDays(1);

    @Override
    public AnomalyMethod method() {
        return AnomalyMethod.SAME_HOUR_BASELINE;
    }

    @Override
    public List<AnomalyCandidate> detect(List<HourlyPoint> series, AnomalyDetectionProperties props) {
        TreeMap<java.time.Instant, BigDecimal> byStart = new TreeMap<>();
        for (HourlyPoint p : series) {
            byStart.put(p.bucketStart(), p.energyKwh());
        }
        List<AnomalyCandidate> out = new ArrayList<>();
        for (HourlyPoint point : series) {
            List<HourlyPoint> samples = new ArrayList<>();
            for (int d = 1; d <= props.getSameHourLookbackDays(); d++) {
                java.time.Instant t = point.bucketStart().minus(DAY.multipliedBy(d));
                BigDecimal v = byStart.get(t);
                if (v != null) {
                    samples.add(new HourlyPoint(t, v, null));
                }
            }
            if (samples.size() < props.getSameHourMinSamples()) {
                continue;
            }
            BigDecimal expected = DetectorMath.mean(samples);
            if (DetectorMath.exceedsThreshold(point.energyKwh(), expected,
                    props.getSameHourThresholdPct(), props)) {
                out.add(DetectorMath.candidate(point, expected, method(),
                        props.getSameHourThresholdPct(),
                        samples.size() + "-day same-hour baseline"));
            }
        }
        return out;
    }
}
