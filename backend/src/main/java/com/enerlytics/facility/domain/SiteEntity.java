package com.enerlytics.facility.domain;

import com.enerlytics.common.domain.AuditedEntity;
import com.enerlytics.organization.domain.OrganizationEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(schema = "org", name = "site")
public class SiteEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "site_code", nullable = false, length = 64)
    private String siteCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "iana_timezone", nullable = false, length = 100)
    private String ianaTimezone;

    @Column(name = "grid_region_code", length = 100)
    private String gridRegionCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "floor_area", precision = 18, scale = 4)
    private BigDecimal floorArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "floor_area_unit", length = 16)
    private FloorAreaUnit floorAreaUnit = FloorAreaUnit.M2;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected SiteEntity() {
    }

    public SiteEntity(OrganizationEntity organization, String siteCode, String name, String ianaTimezone) {
        this.organization = organization;
        this.siteCode = siteCode;
        this.name = name;
        this.ianaTimezone = ianaTimezone;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public String getSiteCode() {
        return siteCode;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getIanaTimezone() {
        return ianaTimezone;
    }

    public void setIanaTimezone(String ianaTimezone) {
        this.ianaTimezone = ianaTimezone;
    }

    public String getGridRegionCode() {
        return gridRegionCode;
    }

    public void setGridRegionCode(String gridRegionCode) {
        this.gridRegionCode = gridRegionCode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public void setOpenedOn(LocalDate openedOn) {
        this.openedOn = openedOn;
    }

    public LocalDate getClosedOn() {
        return closedOn;
    }

    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void archive() {
        this.active = false;
        this.archivedAt = Instant.now();
    }
}
