package com.enerlytics.security.tenant;

import com.enerlytics.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

public final class TenantGuard {

    private TenantGuard() {
    }

    public static void requireCurrentOrganizationOrPlatformAdmin(UserPrincipal principal, UUID requestedOrganizationId) {
        if (principal == null || requestedOrganizationId == null) {
            throw new AccessDeniedException("Tenant context required");
        }
        if (principal.isPlatformAdmin()) {
            return;
        }
        if (!requestedOrganizationId.equals(principal.getCurrentOrganizationId())) {
            throw new AccessDeniedException("Cross-tenant access denied");
        }
    }
}
