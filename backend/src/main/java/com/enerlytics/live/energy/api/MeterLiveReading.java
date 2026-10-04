package com.enerlytics.live.energy.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeterLiveReading(
        UUID meterId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String meterName,
        BigDecimal currentPowerKw,
        Instant lastSeenAt,
        String status) {
}
