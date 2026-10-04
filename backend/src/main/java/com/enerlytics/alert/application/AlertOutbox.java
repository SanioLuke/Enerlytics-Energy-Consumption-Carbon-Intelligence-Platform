package com.enerlytics.alert.application;

import com.enerlytics.alert.api.event.AlertAcknowledgedEvent;
import com.enerlytics.alert.api.event.AlertCreatedEvent;
import com.enerlytics.alert.api.event.AlertResolvedEvent;
import com.enerlytics.alert.domain.AlertInstanceEntity;
import com.enerlytics.telemetry.domain.OutboxEntity;
import com.enerlytics.telemetry.infrastructure.persistence.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AlertOutbox {

    private static final String ALERT_CREATED_TOPIC = "enerlytics.alert.alert-created.v1";
    private static final String ALERT_ACKNOWLEDGED_TOPIC = "enerlytics.alert.alert-acknowledged.v1";
    private static final String ALERT_RESOLVED_TOPIC = "enerlytics.alert.alert-resolved.v1";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AlertOutbox(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void enqueueAlertCreated(UUID organizationId, AlertInstanceEntity instance) {
        AlertCreatedEvent event = AlertEvaluationService.toEvent(instance, Instant.now());
        write(organizationId, instance.getId(), ALERT_CREATED_TOPIC, event);
    }

    public void enqueueAlertAcknowledged(UUID organizationId, AlertInstanceEntity instance, Instant occurredAt) {
        AlertAcknowledgedEvent event = new AlertAcknowledgedEvent(UUID.randomUUID(), occurredAt,
                organizationId, instance.getId(), instance.getAcknowledgedBy(), instance.getAcknowledgedAt());
        write(organizationId, instance.getId(), ALERT_ACKNOWLEDGED_TOPIC, event);
    }

    public void enqueueAlertResolved(UUID organizationId, AlertInstanceEntity instance, Instant occurredAt) {
        AlertResolvedEvent event = new AlertResolvedEvent(UUID.randomUUID(), occurredAt,
                organizationId, instance.getId(), instance.getResolvedBy(), instance.getResolvedAt());
        write(organizationId, instance.getId(), ALERT_RESOLVED_TOPIC, event);
    }

    private void write(UUID organizationId, UUID aggregateId, String topic, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            String key = organizationId.toString() + ":" + aggregateId.toString();
            outboxRepository.save(new OutboxEntity(topic, key, json, null));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize alert event", e);
        }
    }
}
