package com.enerlytics.identity.api.dto;

import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID defaultOrganizationId) {
}
