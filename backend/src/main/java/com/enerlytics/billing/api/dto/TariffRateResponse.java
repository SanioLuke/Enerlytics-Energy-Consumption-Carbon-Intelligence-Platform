package com.enerlytics.billing.api.dto;

import com.enerlytics.billing.domain.TariffDayType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

public record TariffRateResponse(
        UUID id,
        TariffDayType dayType,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        BigDecimal costPerKwh,
        BigDecimal demandRatePerKw,
        int priority) {
}
