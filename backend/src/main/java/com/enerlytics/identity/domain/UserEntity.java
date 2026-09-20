package com.enerlytics.identity.domain;

import com.enerlytics.common.domain.AuditedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "iam", name = "app_user")
public class UserEntity extends AuditedEntity {

    @Column(name = "identity_provider", nullable = false, length = 100)
    private String identityProvider = "local";

    @Column(name = "external_subject", nullable = false, length = 255, unique = true)
    private String externalSubject;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 320)
    private String normalizedEmail;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_sign_in_at")
    private Instant lastSignInAt;

    protected UserEntity() {
    }

    public UserEntity(String email, String displayName) {
        this.email = email;
        this.normalizedEmail = email.toLowerCase();
        this.displayName = displayName;
        this.externalSubject = UUID.randomUUID().toString();
    }

    public UserEntity(String email, String displayName, String passwordHash) {
        this(email, displayName);
        this.passwordHash = passwordHash;
    }

    public String getIdentityProvider() {
        return identityProvider;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public String getEmail() {
        return email;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastSignInAt() {
        return lastSignInAt;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void recordSignIn() {
        this.lastSignInAt = Instant.now();
    }

    public void setExternalSubject(String externalSubject) {
        this.externalSubject = externalSubject;
    }
}
