package com.enerlytics.alert.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID organizationId,
        UUID alertId,
        UUID ruleId,
        String alertType,
        String severity,
        UUID scopeId,
        String scopeType,
        String metric,
        BigDecimal observedValue,
        BigDecimal threshold,
        Instant triggeredAt,
        String contextPayload) {
}
