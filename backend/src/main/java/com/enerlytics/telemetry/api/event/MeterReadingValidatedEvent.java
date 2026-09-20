package com.enerlytics.telemetry.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeterReadingValidatedEvent(
        UUID eventId,
        UUID readingId,
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
