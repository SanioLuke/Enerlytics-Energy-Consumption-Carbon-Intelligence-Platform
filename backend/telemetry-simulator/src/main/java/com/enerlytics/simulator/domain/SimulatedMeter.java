package com.enerlytics.simulator.domain;

import java.util.UUID;

public record SimulatedMeter(
        UUID meterId,
        UUID organizationId,
        UUID siteId,
        int readingIntervalSeconds,
        SimulationProfile profile,
        String timezone) {
}
