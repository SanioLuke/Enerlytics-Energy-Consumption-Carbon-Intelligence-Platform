package com.enerlytics.organization.api.dto;

import com.enerlytics.organization.domain.OrganizationStatus;

import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String organizationKey,
        String displayName,
        OrganizationStatus status,
        String defaultCurrency,
        Integer fiscalYearStartMonth,
        String locale,
        java.time.Instant archivedAt) {
}
