package com.enerlytics.facility.domain;

import com.enerlytics.common.domain.AuditedEntity;
import com.enerlytics.organization.domain.OrganizationEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(schema = "org", name = "zone")
public class ZoneEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private BuildingEntity building;

    @Column(name = "zone_code", nullable = false, length = 64)
    private String zoneCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "zone_type", length = 64)
    private String zoneType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected ZoneEntity() {
    }

    public ZoneEntity(OrganizationEntity organization, SiteEntity site, BuildingEntity building, String zoneCode, String name) {
        this.organization = organization;
        this.site = site;
        this.building = building;
        this.zoneCode = zoneCode;
        this.name = name;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public SiteEntity getSite() {
        return site;
    }

    public BuildingEntity getBuilding() {
        return building;
    }

    public void setBuilding(BuildingEntity building) {
        this.building = building;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getZoneType() {
        return zoneType;
    }

    public void setZoneType(String zoneType) {
        this.zoneType = zoneType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void archive() {
        this.active = false;
        this.archivedAt = Instant.now();
    }
}
