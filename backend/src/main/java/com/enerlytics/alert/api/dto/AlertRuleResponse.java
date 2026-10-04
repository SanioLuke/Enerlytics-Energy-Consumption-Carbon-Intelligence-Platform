package com.enerlytics.alert.api.dto;

import com.enerlytics.analytics.domain.DimensionType;

import java.math.BigDecimal;
import java.util.UUID;

public record AlertRuleResponse(
        UUID id,
        UUID organizationId,
        String name,
        String alertType,
        DimensionType scopeType,
        UUID scopeId,
        String metric,
        String comparisonOperator,
        BigDecimal threshold,
        Long evaluationWindowSeconds,
        String severity,
        Long cooldownSeconds,
        boolean enabled,
        Long version) {
}
