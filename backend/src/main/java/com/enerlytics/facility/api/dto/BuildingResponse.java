package com.enerlytics.facility.api.dto;

import com.enerlytics.facility.domain.FloorAreaUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BuildingResponse(
        UUID id,
        UUID organizationId,
        UUID siteId,
        String code,
        String name,
        String description,
        BigDecimal floorArea,
        FloorAreaUnit floorAreaUnit,
        String buildingType,
        LocalDate commissionedDate,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
