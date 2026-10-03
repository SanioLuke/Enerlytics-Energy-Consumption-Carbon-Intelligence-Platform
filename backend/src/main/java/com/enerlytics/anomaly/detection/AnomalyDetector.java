package com.enerlytics.anomaly.detection;

import com.enerlytics.anomaly.config.AnomalyDetectionProperties;
import com.enerlytics.anomaly.domain.AnomalyMethod;

import java.util.List;

/**
 * Interpretable anomaly detector. Implementations are deterministic functions
 * over a sorted hourly series — the same seam an ML model implements later.
 */
public interface AnomalyDetector {

    AnomalyMethod method();

    /**
     * @param series hourly points sorted ascending by bucketStart; includes baseline
     *               history preceding the detection window. Emit candidates only for
     *               points where the baseline requirements of the method are met.
     */
    List<AnomalyCandidate> detect(List<HourlyPoint> series, AnomalyDetectionProperties props);
}
