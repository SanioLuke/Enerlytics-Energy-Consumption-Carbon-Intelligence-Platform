package com.enerlytics.facility.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ZoneResponse(
        UUID id,
        UUID organizationId,
        UUID siteId,
        UUID buildingId,
        String code,
        String name,
        String description,
        String zoneType,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
