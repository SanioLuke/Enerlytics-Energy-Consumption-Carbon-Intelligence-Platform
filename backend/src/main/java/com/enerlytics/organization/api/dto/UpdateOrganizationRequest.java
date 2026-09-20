package com.enerlytics.organization.api.dto;

import com.enerlytics.organization.domain.OrganizationStatus;
import jakarta.validation.constraints.*;

public record UpdateOrganizationRequest(
        @Size(max = 200) String displayName,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be an ISO 4217 alpha code") String defaultCurrency,
        @Min(1) @Max(12) Integer fiscalYearStartMonth,
        @Pattern(regexp = "^[a-zA-Z]{2}(-[a-zA-Z0-9]+)*$", message = "Locale must be a BCP 47 tag") String locale,
        OrganizationStatus status) {
}
