package com.enerlytics.simulator.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TelemetryEvent {

    private UUID eventId;
    private String schemaVersion = "1.0";
    private UUID meterId;
    private UUID organizationId;
    private UUID siteId;
    private Instant timestamp;
    private BigDecimal energyKwh;
    private BigDecimal powerKw;
    private BigDecimal voltage;
    private BigDecimal current;
    private BigDecimal powerFactor;
    private BigDecimal frequency;
    private String qualityStatus;

    public TelemetryEvent() {
    }

    @JsonProperty("eventId")
    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    @JsonProperty("schemaVersion")
    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    @JsonProperty("meterId")
    public UUID getMeterId() {
        return meterId;
    }

    public void setMeterId(UUID meterId) {
        this.meterId = meterId;
    }

    @JsonProperty("organizationId")
    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    @JsonProperty("siteId")
    public UUID getSiteId() {
        return siteId;
    }

    public void setSiteId(UUID siteId) {
        this.siteId = siteId;
    }

    @JsonProperty("timestamp")
    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @JsonProperty("energyKwh")
    public BigDecimal getEnergyKwh() {
        return energyKwh;
    }

    public void setEnergyKwh(BigDecimal energyKwh) {
        this.energyKwh = energyKwh;
    }

    @JsonProperty("powerKw")
    public BigDecimal getPowerKw() {
        return powerKw;
    }

    public void setPowerKw(BigDecimal powerKw) {
        this.powerKw = powerKw;
    }

    @JsonProperty("voltage")
    public BigDecimal getVoltage() {
        return voltage;
    }

    public void setVoltage(BigDecimal voltage) {
        this.voltage = voltage;
    }

    @JsonProperty("current")
    public BigDecimal getCurrent() {
        return current;
    }

    public void setCurrent(BigDecimal current) {
        this.current = current;
    }

    @JsonProperty("powerFactor")
    public BigDecimal getPowerFactor() {
        return powerFactor;
    }

    public void setPowerFactor(BigDecimal powerFactor) {
        this.powerFactor = powerFactor;
    }

    @JsonProperty("frequency")
    public BigDecimal getFrequency() {
        return frequency;
    }

    public void setFrequency(BigDecimal frequency) {
        this.frequency = frequency;
    }

    @JsonProperty("qualityStatus")
    public String getQualityStatus() {
        return qualityStatus;
    }

    public void setQualityStatus(String qualityStatus) {
        this.qualityStatus = qualityStatus;
    }
}
