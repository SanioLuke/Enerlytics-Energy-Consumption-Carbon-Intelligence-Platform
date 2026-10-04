package com.enerlytics.alert.api.event;

import java.time.Instant;
import java.util.UUID;

public record AlertAcknowledgedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID organizationId,
        UUID alertId,
        String acknowledgedBy,
        Instant acknowledgedAt) {
}
