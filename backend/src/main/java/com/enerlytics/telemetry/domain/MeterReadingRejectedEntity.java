package com.enerlytics.telemetry.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "telemetry", name = "meter_reading_rejected")
public class MeterReadingRejectedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "meter_id")
    private UUID meterId;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "reason_detail")
    private String reasonDetail;

    @Column(name = "event_timestamp")
    private Instant eventTimestamp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MeterReadingRejectedEntity() {
    }

    public MeterReadingRejectedEntity(UUID sourceEventId, String rawPayload, String reasonCode, String reasonDetail) {
        this.sourceEventId = sourceEventId;
        this.rawPayload = rawPayload;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getMeterId() {
        return meterId;
    }

    public void setMeterId(UUID meterId) {
        this.meterId = meterId;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
