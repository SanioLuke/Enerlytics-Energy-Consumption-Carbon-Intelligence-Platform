package com.enerlytics.security.tenant;

import java.util.UUID;

/**
 * Request-scoped holder for the resolved tenant organization.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_ORGANIZATION = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentOrganizationId(UUID organizationId) {
        CURRENT_ORGANIZATION.set(organizationId);
    }

    public static UUID getCurrentOrganizationId() {
        return CURRENT_ORGANIZATION.get();
    }

    public static void clear() {
        CURRENT_ORGANIZATION.remove();
    }
}
