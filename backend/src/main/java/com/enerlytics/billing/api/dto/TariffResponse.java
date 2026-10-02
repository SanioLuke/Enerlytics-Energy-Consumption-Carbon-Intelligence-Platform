package com.enerlytics.billing.api.dto;

import com.enerlytics.billing.domain.TariffType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TariffResponse(
        UUID id,
        UUID organizationId,
        UUID siteId,
        String name,
        TariffType tariffType,
        String currency,
        String timezone,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        List<TariffRateResponse> rates,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
