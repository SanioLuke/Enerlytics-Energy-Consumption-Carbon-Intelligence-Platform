package com.enerlytics.anomaly.detection;

import java.math.BigDecimal;
import java.time.Instant;

/** One hourly aggregate datapoint in a detection time series (sorted by bucketStart). */
public record HourlyPoint(Instant bucketStart, BigDecimal energyKwh, BigDecimal completenessPct) {
}
