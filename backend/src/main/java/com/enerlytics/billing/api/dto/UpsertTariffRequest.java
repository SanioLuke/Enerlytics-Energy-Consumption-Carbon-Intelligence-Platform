package com.enerlytics.billing.api.dto;

import com.enerlytics.billing.domain.TariffType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpsertTariffRequest(
        @NotNull UUID siteId,
        @NotBlank @Size(max = 200) String name,
        @NotNull TariffType tariffType,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        String timezone,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotNull @Valid List<TariffRateRequest> rates) {
}
