package com.enerlytics.meter.api.dto;

import java.util.UUID;

public record SimulatedMeterResponse(
        UUID meterId,
        UUID organizationId,
        UUID siteId,
        UUID buildingId,
        UUID zoneId,
        int readingIntervalSeconds,
        String simulationProfile,
        String timezone) {
}
