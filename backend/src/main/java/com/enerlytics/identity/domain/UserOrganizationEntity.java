package com.enerlytics.identity.domain;

import com.enerlytics.common.domain.AuditedEntity;
import com.enerlytics.organization.domain.OrganizationEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(schema = "iam", name = "user_organization")
public class UserOrganizationEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_status", nullable = false, length = 24)
    private MembershipStatus membershipStatus = MembershipStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private UserEntity invitedByUser;

    @Column(name = "invitation_expires_at")
    private Instant invitationExpiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserOrganizationEntity() {
    }

    public UserOrganizationEntity(OrganizationEntity organization, UserEntity user) {
        this.organization = organization;
        this.user = user;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public UserEntity getUser() {
        return user;
    }

    public MembershipStatus getMembershipStatus() {
        return membershipStatus;
    }

    public void setMembershipStatus(MembershipStatus membershipStatus) {
        this.membershipStatus = membershipStatus;
    }

    public boolean isActive() {
        return membershipStatus == MembershipStatus.ACTIVE;
    }
}
