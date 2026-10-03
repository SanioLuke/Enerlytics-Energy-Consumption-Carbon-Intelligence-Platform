package com.enerlytics.anomaly.detection;

import com.enerlytics.anomaly.config.AnomalyDetectionProperties;
import com.enerlytics.anomaly.domain.AnomalyMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Flags points more than the configured number of population standard
 * deviations from the trailing N-hour mean. On a zero-variance window any
 * differing value is a definite anomaly (maximum confidence).
 */
@Component
public class RollingZScoreDetector implements AnomalyDetector {

    @Override
    public AnomalyMethod method() {
        return AnomalyMethod.ROLLING_ZSCORE;
    }

    @Override
    public List<AnomalyCandidate> detect(List<HourlyPoint> series, AnomalyDetectionProperties props) {
        int window = props.getRollingWindowHours();
        List<AnomalyCandidate> out = new ArrayList<>();
        for (int i = window; i < series.size(); i++) {
            HourlyPoint point = series.get(i);
            List<HourlyPoint> windowPoints = DetectorMath.subList(series, i - window, i);
            if (!DetectorMath.isContiguous(windowPoints, point)) {
                continue;
            }
            BigDecimal mean = DetectorMath.mean(windowPoints);
            if (DetectorMath.belowExpectedFloor(mean, props)) {
                continue;
            }
            BigDecimal stddev = DetectorMath.stddev(windowPoints, mean);
            boolean anomalous;
            BigDecimal z;
            if (stddev.compareTo(BigDecimal.ZERO) == 0) {
                anomalous = point.energyKwh().compareTo(mean) != 0;
                z = anomalous ? props.getZscoreThreshold().multiply(BigDecimal.TEN) : BigDecimal.ZERO;
            } else {
                z = point.energyKwh().subtract(mean, DetectorMath.MC)
                        .divide(stddev, DetectorMath.MC).abs();
                anomalous = z.compareTo(props.getZscoreThreshold()) > 0;
            }
            if (anomalous) {
                AnomalyCandidate base = DetectorMath.candidate(point, mean, method(),
                        props.getRollingMeanThresholdPct(), "z-score " + z.setScale(3, java.math.RoundingMode.HALF_EVEN)
                                + " vs trailing " + window + "h mean");
                BigDecimal confidence = z.divide(props.getZscoreThreshold().multiply(BigDecimal.valueOf(2)), DetectorMath.MC)
                        .min(DetectorMath.CONFIDENCE_CAP).setScale(4, java.math.RoundingMode.HALF_EVEN);
                out.add(new AnomalyCandidate(base.bucketStart(), base.actualKwh(), base.expectedKwh(),
                        base.deviationPct(), base.method(), base.severity(), confidence,
                        base.explanation()));
            }
        }
        return out;
    }
}
