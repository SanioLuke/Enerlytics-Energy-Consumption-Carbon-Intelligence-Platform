package com.enerlytics.identity.domain;

import com.enerlytics.common.domain.AuditedEntity;
import com.enerlytics.organization.domain.OrganizationEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(schema = "iam", name = "role_assignment")
public class RoleAssignmentEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_organization_id", nullable = false)
    private UserOrganizationEntity userOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom = Instant.now();

    @Column(name = "valid_to")
    private Instant validTo;

    protected RoleAssignmentEntity() {
    }

    public RoleAssignmentEntity(OrganizationEntity organization, UserOrganizationEntity userOrganization, RoleEntity role) {
        this.organization = organization;
        this.userOrganization = userOrganization;
        this.role = role;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public UserOrganizationEntity getUserOrganization() {
        return userOrganization;
    }

    public RoleEntity getRole() {
        return role;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public boolean isActiveAt(Instant instant) {
        return !instant.isBefore(validFrom) && (validTo == null || instant.isBefore(validTo));
    }
}
