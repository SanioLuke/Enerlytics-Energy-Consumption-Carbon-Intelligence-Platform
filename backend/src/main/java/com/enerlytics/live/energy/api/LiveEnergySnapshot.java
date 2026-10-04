package com.enerlytics.live.energy.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot emitted over SSE to the live-energy monitoring page. Contains the
 * latest aggregate demand, meter status, and recent trend for a requested
 * scope (organization / site / building). Missing optional scope ids mean
 * the snapshot is for the broadest requested scope.
 */
public record LiveEnergySnapshot(
        Instant generatedAt,
        UUID organizationId,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID siteId,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID buildingId,
        BigDecimal currentDemandKw,
        Integer activeMeterCount,
        Integer offlineMeterCount,
        List<MeterLiveReading> meterReadings,
        List<TrendPoint> recentTrend) {

    public LiveEnergySnapshot {
        meterReadings = meterReadings != null ? List.copyOf(meterReadings) : List.of();
        recentTrend = recentTrend != null ? List.copyOf(recentTrend) : List.of();
    }
}
