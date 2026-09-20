package com.enerlytics.telemetry.application;

import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.telemetry.api.event.MeterReadingReceivedEvent;
import com.enerlytics.telemetry.api.event.MeterReadingRejectedEvent;
import com.enerlytics.telemetry.api.event.MeterReadingValidatedEvent;
import com.enerlytics.telemetry.domain.MeterReadingEntity;
import com.enerlytics.telemetry.domain.MeterReadingRejectedEntity;
import com.enerlytics.telemetry.domain.OutboxEntity;
import com.enerlytics.telemetry.infrastructure.persistence.MeterReadingRejectedRepository;
import com.enerlytics.telemetry.infrastructure.persistence.MeterReadingRepository;
import com.enerlytics.telemetry.infrastructure.persistence.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TelemetryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final TelemetryValidator validator;
    private final MeterReadingRepository readingRepository;
    private final MeterReadingRejectedRepository rejectedRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String validatedTopic;
    private final String rejectedTopic;

    public TelemetryIngestionService(TelemetryValidator validator,
                                     MeterReadingRepository readingRepository,
                                     MeterReadingRejectedRepository rejectedRepository,
                                     OutboxRepository outboxRepository,
                                     ObjectMapper objectMapper,
                                     @Value("${enerlytics.kafka.topics.meter-reading-validated:enerlytics.telemetry.meter-reading-validated.v1}") String validatedTopic,
                                     @Value("${enerlytics.kafka.topics.meter-reading-rejected:enerlytics.telemetry.meter-reading-rejected.v1}") String rejectedTopic) {
        this.validator = validator;
        this.readingRepository = readingRepository;
        this.rejectedRepository = rejectedRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.validatedTopic = validatedTopic;
        this.rejectedTopic = rejectedTopic;
    }

    @Transactional
    public void ingest(MeterReadingReceivedEvent event) {
        if (event == null || event.eventId() == null) {
            log.warn("Ignoring null or unidentifiable telemetry event");
            return;
        }

        if (readingRepository.existsBySourceEventId(event.eventId()) ||
            rejectedRepository.existsBySourceEventId(event.eventId())) {
            log.debug("Duplicate source event {} ignored", event.eventId());
            return;
        }

        TelemetryValidationResult result = validator.validate(event);
        if (result instanceof TelemetryValidationResult.Valid valid) {
            persistValid(valid.event(), valid.meter());
        } else if (result instanceof TelemetryValidationResult.Rejected rejected) {
            persistRejected(rejected.event(), rejected.reasonCode(), rejected.reasonDetail());
        }
    }

    private void persistValid(MeterReadingReceivedEvent event, MeterEntity meter) {
        MeterReadingEntity reading = new MeterReadingEntity(
                meter.getOrganization().getId(),
                meter.getId(),
                event.timestamp(),
                event.energyKwh(),
                event.eventId());
        reading.setPowerKw(event.powerKw());
        reading.setVoltage(event.voltage());
        reading.setCurrent(event.current());
        reading.setPowerFactor(event.powerFactor());
        reading.setFrequency(event.frequency());
        reading.setQualityStatus(event.qualityStatus());

        MeterReadingEntity saved = readingRepository.save(reading);

        MeterReadingValidatedEvent validatedEvent = new MeterReadingValidatedEvent(
                event.eventId(),
                saved.getId(),
                meter.getId(),
                meter.getOrganization().getId(),
                meter.getSite().getId(),
                event.timestamp(),
                event.energyKwh(),
                event.powerKw(),
                event.voltage(),
                event.current(),
                event.powerFactor(),
                event.frequency(),
                event.qualityStatus());

        writeOutbox(validatedTopic, partitionKey(meter), validatedEvent);
    }

    private void persistRejected(MeterReadingReceivedEvent event, String reasonCode, String reasonDetail) {
        MeterReadingRejectedEntity rejected = new MeterReadingRejectedEntity(
                event.eventId(),
                payloadForRejected(event),
                reasonCode,
                reasonDetail);
        if (event.organizationId() != null) {
            rejected.setOrganizationId(event.organizationId());
        }
        if (event.meterId() != null) {
            rejected.setMeterId(event.meterId());
        }
        rejected.setEventTimestamp(event.timestamp());

        rejectedRepository.save(rejected);

        MeterReadingRejectedEvent rejectedEvent = new MeterReadingRejectedEvent(
                event.eventId(),
                event.meterId(),
                event.organizationId(),
                reasonCode,
                reasonDetail,
                event.timestamp());

        writeOutbox(rejectedTopic, partitionKey(event.organizationId(), event.meterId()), rejectedEvent);
    }

    private void writeOutbox(String topic, String partitionKey, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(new OutboxEntity(topic, partitionKey, json, null));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }

    private String payloadForRejected(MeterReadingReceivedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize rejected raw payload: {}", e.getMessage());
            return "";
        }
    }

    private String partitionKey(MeterEntity meter) {
        return meter.getOrganization().getId() + ":" + meter.getId();
    }

    private String partitionKey(UUID organizationId, UUID meterId) {
        return (organizationId != null ? organizationId : "_") + ":" + (meterId != null ? meterId : "_");
    }
}
