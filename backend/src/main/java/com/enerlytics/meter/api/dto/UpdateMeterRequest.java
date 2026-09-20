package com.enerlytics.meter.api.dto;

import com.enerlytics.meter.domain.MeterType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Map;

public record UpdateMeterRequest(
        @Size(max = 200) String name,
        @Size(max = 100) String serialNumber,
        @Size(max = 100) String manufacturer,
        @Size(max = 100) String model,
        MeterType meterType,
        LocalDate installationDate,
        @Min(1) @Max(3600) Integer readingIntervalSeconds,
        @Size(max = 20) String unit,
        Map<String, String> metadata) {
}
