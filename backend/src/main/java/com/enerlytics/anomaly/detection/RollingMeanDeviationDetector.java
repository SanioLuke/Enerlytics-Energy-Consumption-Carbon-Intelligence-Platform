package com.enerlytics.anomaly.detection;

import com.enerlytics.anomaly.config.AnomalyDetectionProperties;
import com.enerlytics.anomaly.domain.AnomalyMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Flags points whose consumption deviates from the trailing N-hour mean by more
 * than the configured percentage. Requires the full window of history, so the
 * first N hours of a series can never produce an alert.
 */
@Component
public class RollingMeanDeviationDetector implements AnomalyDetector {

    @Override
    public AnomalyMethod method() {
        return AnomalyMethod.ROLLING_MEAN_DEVIATION;
    }

    @Override
    public List<AnomalyCandidate> detect(List<HourlyPoint> series, AnomalyDetectionProperties props) {
        int window = props.getRollingWindowHours();
        List<AnomalyCandidate> out = new ArrayList<>();
        for (int i = window; i < series.size(); i++) {
            HourlyPoint point = series.get(i);
            List<HourlyPoint> baseline = DetectorMath.subList(series, i - window, i);
            if (!DetectorMath.isContiguous(baseline, point)) {
                continue;
            }
            BigDecimal expected = DetectorMath.mean(baseline);
            if (DetectorMath.exceedsThreshold(point.energyKwh(), expected,
                    props.getRollingMeanThresholdPct(), props)) {
                out.add(DetectorMath.candidate(point, expected, method(),
                        props.getRollingMeanThresholdPct(), "trailing " + window + "h mean"));
            }
        }
        return out;
    }
}
