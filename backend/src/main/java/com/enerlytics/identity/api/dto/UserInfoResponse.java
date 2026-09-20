package com.enerlytics.identity.api.dto;

import com.enerlytics.identity.domain.UserStatus;

import java.util.List;
import java.util.UUID;

public record UserInfoResponse(
        UUID id,
        String email,
        String displayName,
        UserStatus status,
        List<OrganizationMembershipResponse> organizations) {

    public record OrganizationMembershipResponse(
            UUID organizationId,
            String organizationKey,
            String organizationName,
            List<String> roles,
            List<String> permissions) {
    }
}
