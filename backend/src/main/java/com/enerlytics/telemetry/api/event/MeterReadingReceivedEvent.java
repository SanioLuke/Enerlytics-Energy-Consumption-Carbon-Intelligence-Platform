package com.enerlytics.telemetry.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeterReadingReceivedEvent(
        UUID eventId,
        String schemaVersion,
        UUID meterId,
        UUID organizationId,
        UUID siteId,
        Instant timestamp,
        BigDecimal energyKwh,
        BigDecimal powerKw,
        BigDecimal voltage,
        BigDecimal current,
        BigDecimal powerFactor,
        BigDecimal frequency,
        String qualityStatus) {
}
