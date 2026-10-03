package com.enerlytics.anomaly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "enerlytics.anomaly")
public class AnomalyDetectionProperties {

    /** Trailing hours used by the rolling mean/z-score detectors. */
    private int rollingWindowHours = 24;

    /** Deviation percentage above which the rolling-mean detector fires. */
    private BigDecimal rollingMeanThresholdPct = new BigDecimal("50");

    /** Absolute z-score above which the z-score detector fires. */
    private BigDecimal zscoreThreshold = new BigDecimal("3");

    /** Days of history used for the same-hour baseline. */
    private int sameHourLookbackDays = 14;

    /** Minimum same-hour samples required before the baseline detector fires. */
    private int sameHourMinSamples = 5;

    /** Deviation percentage above which the same-hour detector fires. */
    private BigDecimal sameHourThresholdPct = new BigDecimal("60");

    /** Deviation percentage above which the previous-day detector fires. */
    private BigDecimal percentageThresholdPct = new BigDecimal("75");

    /** Hours since a dimension's first aggregate during which alerts are suppressed. */
    private long startupHours = 48;

    /** Days of history loaded before the detection window for baselines. */
    private int baselineLookbackDays = 35;

    /** Completeness below which a bucket is not evaluated (missing telemetry). */
    private BigDecimal minCompletenessPct = new BigDecimal("50");

    /** Expected values below this floor are not meaningfully comparable. */
    private BigDecimal minExpectedKwh = new BigDecimal("0.001");

    public int getRollingWindowHours() { return rollingWindowHours; }
    public void setRollingWindowHours(int v) { this.rollingWindowHours = v; }
    public BigDecimal getRollingMeanThresholdPct() { return rollingMeanThresholdPct; }
    public void setRollingMeanThresholdPct(BigDecimal v) { this.rollingMeanThresholdPct = v; }
    public BigDecimal getZscoreThreshold() { return zscoreThreshold; }
    public void setZscoreThreshold(BigDecimal v) { this.zscoreThreshold = v; }
    public int getSameHourLookbackDays() { return sameHourLookbackDays; }
    public void setSameHourLookbackDays(int v) { this.sameHourLookbackDays = v; }
    public int getSameHourMinSamples() { return sameHourMinSamples; }
    public void setSameHourMinSamples(int v) { this.sameHourMinSamples = v; }
    public BigDecimal getSameHourThresholdPct() { return sameHourThresholdPct; }
    public void setSameHourThresholdPct(BigDecimal v) { this.sameHourThresholdPct = v; }
    public BigDecimal getPercentageThresholdPct() { return percentageThresholdPct; }
    public void setPercentageThresholdPct(BigDecimal v) { this.percentageThresholdPct = v; }
    public long getStartupHours() { return startupHours; }
    public void setStartupHours(long v) { this.startupHours = v; }
    public int getBaselineLookbackDays() { return baselineLookbackDays; }
    public void setBaselineLookbackDays(int v) { this.baselineLookbackDays = v; }
    public BigDecimal getMinCompletenessPct() { return minCompletenessPct; }
    public void setMinCompletenessPct(BigDecimal v) { this.minCompletenessPct = v; }
    public BigDecimal getMinExpectedKwh() { return minExpectedKwh; }
    public void setMinExpectedKwh(BigDecimal v) { this.minExpectedKwh = v; }
}
