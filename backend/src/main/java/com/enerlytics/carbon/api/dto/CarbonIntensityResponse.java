package com.enerlytics.carbon.api.dto;

import java.time.Instant;

public record CarbonIntensityResponse(
        String zone,
        int carbonIntensityGCo2EqPerKwh,
        boolean estimated,
        Instant retrievedAt) {
}
