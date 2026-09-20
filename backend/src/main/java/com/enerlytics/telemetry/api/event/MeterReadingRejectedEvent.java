package com.enerlytics.telemetry.api.event;

import java.time.Instant;
import java.util.UUID;

public record MeterReadingRejectedEvent(
        UUID sourceEventId,
        UUID meterId,
        UUID organizationId,
        String reasonCode,
        String reasonDetail,
        Instant timestamp) {
}
