package com.enerlytics.carbon.api.dto;

public record ProviderQualityResponse(
        String zone,
        long totalHours,
        long hoursWithFactor,
        long estimatedHours,
        long missingHours,
        long staleHours) {
}
