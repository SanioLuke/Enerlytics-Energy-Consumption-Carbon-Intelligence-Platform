package com.enerlytics.meter.domain;

import com.enerlytics.common.domain.AuditedEntity;
import com.enerlytics.facility.domain.BuildingEntity;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.domain.ZoneEntity;
import com.enerlytics.organization.domain.OrganizationEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(schema = "telemetry", name = "meter")
public class MeterEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    private BuildingEntity building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private ZoneEntity zone;

    @Column(name = "meter_code", nullable = false, length = 64)
    private String meterCode;

    @Column(name = "meter_name", nullable = false, length = 200)
    private String meterName;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    @Column(name = "model", length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type", length = 64)
    private MeterType meterType;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MeterStatus status = MeterStatus.PROVISIONING;

    @Column(name = "reading_interval_seconds", nullable = false)
    private Integer readingIntervalSeconds;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "kWh";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "commissioned_at")
    private Instant commissionedAt;

    @Column(name = "decommissioned_at")
    private Instant decommissionedAt;

    @Column(name = "metadata")
    private String metadata;

    protected MeterEntity() {
    }

    public MeterEntity(OrganizationEntity organization, SiteEntity site, String meterCode, String meterName, Integer readingIntervalSeconds) {
        this.organization = organization;
        this.site = site;
        this.meterCode = meterCode;
        this.meterName = meterName;
        this.readingIntervalSeconds = readingIntervalSeconds;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public SiteEntity getSite() {
        return site;
    }

    public void setSite(SiteEntity site) {
        this.site = site;
    }

    public BuildingEntity getBuilding() {
        return building;
    }

    public void setBuilding(BuildingEntity building) {
        this.building = building;
    }

    public ZoneEntity getZone() {
        return zone;
    }

    public void setZone(ZoneEntity zone) {
        this.zone = zone;
    }

    public String getMeterCode() {
        return meterCode;
    }

    public String getMeterName() {
        return meterName;
    }

    public void setMeterName(String meterName) {
        this.meterName = meterName;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public MeterType getMeterType() {
        return meterType;
    }

    public void setMeterType(MeterType meterType) {
        this.meterType = meterType;
    }

    public LocalDate getInstallationDate() {
        return installationDate;
    }

    public void setInstallationDate(LocalDate installationDate) {
        this.installationDate = installationDate;
    }

    public MeterStatus getStatus() {
        return status;
    }

    public void setStatus(MeterStatus status) {
        this.status = status;
    }

    public Integer getReadingIntervalSeconds() {
        return readingIntervalSeconds;
    }

    public void setReadingIntervalSeconds(Integer readingIntervalSeconds) {
        this.readingIntervalSeconds = readingIntervalSeconds;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getCommissionedAt() {
        return commissionedAt;
    }

    public void setCommissionedAt(Instant commissionedAt) {
        this.commissionedAt = commissionedAt;
    }

    public Instant getDecommissionedAt() {
        return decommissionedAt;
    }

    public void setDecommissionedAt(Instant decommissionedAt) {
        this.decommissionedAt = decommissionedAt;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
