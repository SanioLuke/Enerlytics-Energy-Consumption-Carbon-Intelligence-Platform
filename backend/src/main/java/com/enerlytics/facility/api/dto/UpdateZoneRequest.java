package com.enerlytics.facility.api.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateZoneRequest(
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 64) String zoneType,
        UUID buildingId) {
}
