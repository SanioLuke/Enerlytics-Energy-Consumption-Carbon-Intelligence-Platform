package com.enerlytics.live.energy.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "enerlytics.live.energy")
public class LiveEnergyProperties {

    /** Snapshot publish cadence in milliseconds. */
    private long publishIntervalMs = 1000L;
    /** A meter is considered offline if no reading arrives within this window. */
    private long offlineThresholdMs = 300_000L;
    /** Minimum time between trend points in milliseconds. */
    private long trendResolutionMs = 10_000L;
    /** Maximum number of trend points retained per scope. */
    private int trendMaxPoints = 360;
    /** SSE emitter timeout in milliseconds. */
    private long emitterTimeoutMs = 1_800_000L;
    /** Meters included in each snapshot. */
    private int maxMeterReadingsPerSnapshot = 50;

    public long getPublishIntervalMs() {
        return publishIntervalMs;
    }

    public void setPublishIntervalMs(long publishIntervalMs) {
        this.publishIntervalMs = publishIntervalMs;
    }

    public long getOfflineThresholdMs() {
        return offlineThresholdMs;
    }

    public void setOfflineThresholdMs(long offlineThresholdMs) {
        this.offlineThresholdMs = offlineThresholdMs;
    }

    public long getTrendResolutionMs() {
        return trendResolutionMs;
    }

    public void setTrendResolutionMs(long trendResolutionMs) {
        this.trendResolutionMs = trendResolutionMs;
    }

    public int getTrendMaxPoints() {
        return trendMaxPoints;
    }

    public void setTrendMaxPoints(int trendMaxPoints) {
        this.trendMaxPoints = trendMaxPoints;
    }

    public long getEmitterTimeoutMs() {
        return emitterTimeoutMs;
    }

    public void setEmitterTimeoutMs(long emitterTimeoutMs) {
        this.emitterTimeoutMs = emitterTimeoutMs;
    }

    public int getMaxMeterReadingsPerSnapshot() {
        return maxMeterReadingsPerSnapshot;
    }

    public void setMaxMeterReadingsPerSnapshot(int maxMeterReadingsPerSnapshot) {
        this.maxMeterReadingsPerSnapshot = maxMeterReadingsPerSnapshot;
    }
}
