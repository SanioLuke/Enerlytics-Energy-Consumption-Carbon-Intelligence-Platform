package com.enerlytics.identity.domain;

import com.enerlytics.common.domain.AuditedEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(schema = "iam", name = "refresh_token")
public class RefreshTokenEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "replaced_by_token_hash", length = 255)
    private String replacedByTokenHash;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(UserEntity user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UserEntity getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsable(Instant now) {
        return !revoked && !isExpired(now);
    }

    public void revoke(Instant now) {
        this.revoked = true;
        this.revokedAt = now;
    }

    public void markReplacedBy(String newTokenHash) {
        this.replacedByTokenHash = newTokenHash;
    }

    public String getReplacedByTokenHash() {
        return replacedByTokenHash;
    }
}
