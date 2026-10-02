package com.enerlytics.billing.api.dto;

import com.enerlytics.billing.domain.TariffDayType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

public record TariffRateRequest(
        @NotNull TariffDayType dayType,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @NotNull @DecimalMin("0") BigDecimal costPerKwh,
        @DecimalMin("0") BigDecimal demandRatePerKw,
        Integer priority) {
}
