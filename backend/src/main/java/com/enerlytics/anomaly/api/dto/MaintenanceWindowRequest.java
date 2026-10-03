package com.enerlytics.anomaly.api.dto;

import com.enerlytics.analytics.domain.DimensionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceWindowRequest(
        @NotNull DimensionType entityType,
        @NotNull UUID entityId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 500) String reason) {
}
