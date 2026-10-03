package com.enerlytics.anomaly.api.dto;

import com.enerlytics.analytics.domain.DimensionType;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceWindowResponse(
        UUID id,
        DimensionType entityType,
        UUID entityId,
        Instant startsAt,
        Instant endsAt,
        String reason,
        boolean active,
        String createdBy) {
}
