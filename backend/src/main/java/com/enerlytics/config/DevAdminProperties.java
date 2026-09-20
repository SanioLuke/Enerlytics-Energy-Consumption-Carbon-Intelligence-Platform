package com.enerlytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Development-only seed admin credentials.
 *
 * <p>Leave email/password blank in any environment where an admin account
 * should not be auto-created.</p>
 */
@ConfigurationProperties(prefix = "enerlytics.security.dev-admin")
public record DevAdminProperties(
        String email,
        String password,
        String displayName,
        String organizationKey,
        String organizationName) {
}
