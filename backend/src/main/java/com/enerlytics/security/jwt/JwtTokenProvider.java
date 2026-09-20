package com.enerlytics.security.jwt;

import com.enerlytics.identity.domain.UserEntity;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String DEFAULT_ORGANIZATION_CLAIM = "defaultOrganizationId";
    private static final String ENABLED_CLAIM = "enabled";
    private static final String EMAIL_CLAIM = "email";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final JwtProperties properties;
    private final SecretKey accessKey;
    private final SecretKey refreshKey;

    public JwtProperties properties() {
        return properties;
    }

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.accessKey = deriveKey(properties.accessSecret());
        this.refreshKey = deriveKey(properties.refreshSecret());
    }

    public String generateAccessToken(UserEntity user, UUID defaultOrganizationId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.accessExpirationMs());
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(ENABLED_CLAIM, user.isActive())
                .claim(DEFAULT_ORGANIZATION_CLAIM, defaultOrganizationId != null ? defaultOrganizationId.toString() : null)
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(accessKey)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token);
        Claims claims = jws.getPayload();
        if (!ACCESS_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new JwtException("Invalid token type");
        }
        return new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                claims.get(EMAIL_CLAIM, String.class),
                Boolean.TRUE.equals(claims.get(ENABLED_CLAIM, Boolean.class)),
                parseOptionalUuid(claims.get(DEFAULT_ORGANIZATION_CLAIM, String.class))
        );
    }

    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_ENCODER.encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return BASE64_ENCODER.encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash refresh token", e);
        }
    }

    private static SecretKey deriveKey(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 characters)");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID parseOptionalUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    public record AccessTokenClaims(UUID userId, String email, boolean enabled, UUID defaultOrganizationId) {
    }
}
