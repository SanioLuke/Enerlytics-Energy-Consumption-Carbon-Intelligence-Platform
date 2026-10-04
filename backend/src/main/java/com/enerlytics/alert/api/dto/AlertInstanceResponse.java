package com.enerlytics.alert.api.dto;

import com.enerlytics.analytics.domain.DimensionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertInstanceResponse(
        UUID id,
        UUID organizationId,
        UUID ruleId,
        String ruleName,
        DimensionType scopeType,
        UUID scopeId,
        String alertType,
        String metric,
        BigDecimal observedValue,
        BigDecimal threshold,
        String severity,
        String status,
        Instant triggeredAt,
        Instant acknowledgedAt,
        String acknowledgedBy,
        Instant resolvedAt,
        String resolvedBy,
        String contextPayload) {
}
