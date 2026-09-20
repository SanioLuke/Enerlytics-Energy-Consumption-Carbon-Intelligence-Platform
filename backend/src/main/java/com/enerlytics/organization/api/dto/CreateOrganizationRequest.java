package com.enerlytics.organization.api.dto;

import com.enerlytics.organization.domain.OrganizationStatus;
import jakarta.validation.constraints.*;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 64) String organizationKey,
        @NotBlank @Size(max = 200) String displayName,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be an ISO 4217 alpha code") String defaultCurrency,
        @NotNull @Min(1) @Max(12) Integer fiscalYearStartMonth,
        @NotBlank @Pattern(regexp = "^[a-zA-Z]{2}(-[a-zA-Z0-9]+)*$", message = "Locale must be a BCP 47 tag") String locale,
        @NotNull OrganizationStatus status) {
}
