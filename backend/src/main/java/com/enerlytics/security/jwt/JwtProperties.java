package com.enerlytics.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing and expiration configuration.
 *
 * <p>Secrets must be generated outside the application (e.g. from a secret manager)
 * and injected through the environment. The fallback values are suitable only for
 * local development and are intentionally weak placeholders.</p>
 */
@ConfigurationProperties(prefix = "enerlytics.security.jwt")
public record JwtProperties(
        String accessSecret,
        String refreshSecret,
        long accessExpirationMs,
        long refreshExpirationMs) {
}
