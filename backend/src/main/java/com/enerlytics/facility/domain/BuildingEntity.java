package com.enerlytics.facility.domain;

import com.enerlytics.common.domain.AuditedEntity;
import com.enerlytics.organization.domain.OrganizationEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(schema = "org", name = "building")
public class BuildingEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(name = "building_code", nullable = false, length = 64)
    private String buildingCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "floor_area", precision = 18, scale = 4)
    private BigDecimal floorArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "floor_area_unit", length = 16)
    private FloorAreaUnit floorAreaUnit = FloorAreaUnit.M2;

    @Column(name = "building_type", length = 64)
    private String buildingType;

    @Column(name = "commissioned_date")
    private LocalDate commissionedDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected BuildingEntity() {
    }

    public BuildingEntity(OrganizationEntity organization, SiteEntity site, String buildingCode, String name) {
        this.organization = organization;
        this.site = site;
        this.buildingCode = buildingCode;
        this.name = name;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public SiteEntity getSite() {
        return site;
    }

    public String getBuildingCode() {
        return buildingCode;
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

    public BigDecimal getFloorArea() {
        return floorArea;
    }

    public void setFloorArea(BigDecimal floorArea) {
        this.floorArea = floorArea;
    }

    public FloorAreaUnit getFloorAreaUnit() {
        return floorAreaUnit;
    }

    public void setFloorAreaUnit(FloorAreaUnit floorAreaUnit) {
        this.floorAreaUnit = floorAreaUnit;
    }

    public String getBuildingType() {
        return buildingType;
    }

    public void setBuildingType(String buildingType) {
        this.buildingType = buildingType;
    }

    public LocalDate getCommissionedDate() {
        return commissionedDate;
    }

    public void setCommissionedDate(LocalDate commissionedDate) {
        this.commissionedDate = commissionedDate;
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
