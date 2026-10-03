package com.enerlytics.anomaly.domain;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.common.domain.AuditedEntity;
import com.enerlytics.organization.domain.OrganizationEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "anomaly", name = "maintenance_window")
public class MaintenanceWindowEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension_type", nullable = false, length = 16)
    private DimensionType dimensionType;

    @Column(name = "dimension_id", nullable = false)
    private UUID dimensionId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    protected MaintenanceWindowEntity() {
    }

    public MaintenanceWindowEntity(OrganizationEntity organization, DimensionType dimensionType,
                                   UUID dimensionId, Instant startsAt, Instant endsAt, String reason,
                                   String createdBy) {
        this.organization = organization;
        this.dimensionType = dimensionType;
        this.dimensionId = dimensionId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public boolean covers(Instant instant) {
        return active && !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }

    public void deactivate() {
        this.active = false;
    }

    public OrganizationEntity getOrganization() { return organization; }
    public DimensionType getDimensionType() { return dimensionType; }
    public UUID getDimensionId() { return dimensionId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getReason() { return reason; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
}
