package com.enerlytics.meter.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignLocationRequest(
        @NotNull UUID siteId,
        UUID buildingId,
        UUID zoneId) {
}
