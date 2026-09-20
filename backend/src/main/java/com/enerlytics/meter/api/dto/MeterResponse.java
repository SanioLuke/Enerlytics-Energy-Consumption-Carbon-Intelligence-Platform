package com.enerlytics.meter.api.dto;

import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.domain.MeterType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record MeterResponse(
        UUID id,
        UUID organizationId,
        UUID siteId,
        UUID buildingId,
        UUID zoneId,
        String code,
        String name,
        String serialNumber,
        String manufacturer,
        String model,
        MeterType meterType,
        LocalDate installationDate,
        MeterStatus status,
        Integer readingIntervalSeconds,
        String unit,
        Instant lastSeenAt,
        Instant commissionedAt,
        Instant decommissionedAt,
        Map<String, String> metadata,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
