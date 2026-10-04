package com.enerlytics.alert.api.event;

import java.time.Instant;
import java.util.UUID;

public record AlertResolvedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID organizationId,
        UUID alertId,
        String resolvedBy,
        Instant resolvedAt) {
}
