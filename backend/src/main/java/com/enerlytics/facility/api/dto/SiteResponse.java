package com.enerlytics.facility.api.dto;

import com.enerlytics.facility.domain.FloorAreaUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SiteResponse(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        String description,
        String address,
        String country,
        String state,
        String city,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String timezone,
        String gridRegionCode,
        String currency,
        BigDecimal floorArea,
        FloorAreaUnit floorAreaUnit,
        boolean active,
        LocalDate openedOn,
        LocalDate closedOn,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
