package com.enerlytics.telemetry.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "telemetry", name = "meter_reading")
public class MeterReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "meter_id", nullable = false)
    private UUID meterId;

    @Column(name = "channel_id")
    private UUID channelId;

    @Column(name = "sample_timestamp", nullable = false)
    private Instant sampleTimestamp;

    @Column(name = "energy_kwh", nullable = false, precision = 18, scale = 9)
    private BigDecimal energyKwh;

    @Column(name = "power_kw", precision = 12, scale = 6)
    private BigDecimal powerKw;

    @Column(name = "voltage", precision = 8, scale = 3)
    private BigDecimal voltage;

    @Column(name = "current", precision = 12, scale = 6)
    private BigDecimal current;

    @Column(name = "power_factor", precision = 5, scale = 4)
    private BigDecimal powerFactor;

    @Column(name = "frequency", precision = 6, scale = 3)
    private BigDecimal frequency;

    @Column(name = "quality_status", length = 32)
    private String qualityStatus;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "metadata")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MeterReadingEntity() {
    }

    public MeterReadingEntity(UUID organizationId, UUID meterId, Instant sampleTimestamp,
                              BigDecimal energyKwh, UUID sourceEventId) {
        this.organizationId = organizationId;
        this.meterId = meterId;
        this.sampleTimestamp = sampleTimestamp;
        this.energyKwh = energyKwh;
        this.sourceEventId = sourceEventId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getMeterId() {
        return meterId;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public void setChannelId(UUID channelId) {
        this.channelId = channelId;
    }

    public Instant getSampleTimestamp() {
        return sampleTimestamp;
    }

    public BigDecimal getEnergyKwh() {
        return energyKwh;
    }

    public BigDecimal getPowerKw() {
        return powerKw;
    }

    public void setPowerKw(BigDecimal powerKw) {
        this.powerKw = powerKw;
    }

    public BigDecimal getVoltage() {
        return voltage;
    }

    public void setVoltage(BigDecimal voltage) {
        this.voltage = voltage;
    }

    public BigDecimal getCurrent() {
        return current;
    }

    public void setCurrent(BigDecimal current) {
        this.current = current;
    }

    public BigDecimal getPowerFactor() {
        return powerFactor;
    }

    public void setPowerFactor(BigDecimal powerFactor) {
        this.powerFactor = powerFactor;
    }

    public BigDecimal getFrequency() {
        return frequency;
    }

    public void setFrequency(BigDecimal frequency) {
        this.frequency = frequency;
    }

    public String getQualityStatus() {
        return qualityStatus;
    }

    public void setQualityStatus(String qualityStatus) {
        this.qualityStatus = qualityStatus;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
