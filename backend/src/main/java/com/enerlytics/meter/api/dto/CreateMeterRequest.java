package com.enerlytics.meter.api.dto;

import com.enerlytics.meter.domain.MeterType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CreateMeterRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String serialNumber,
        @Size(max = 100) String manufacturer,
        @Size(max = 100) String model,
        @NotNull MeterType meterType,
        LocalDate installationDate,
        @NotNull @Min(1) @Max(3600) Integer readingIntervalSeconds,
        @NotBlank @Size(max = 20) String unit,
        @NotNull UUID siteId,
        UUID buildingId,
        UUID zoneId,
        Map<String, String> metadata) {
}
