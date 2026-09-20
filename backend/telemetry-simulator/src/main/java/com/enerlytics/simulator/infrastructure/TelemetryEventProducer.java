package com.enerlytics.simulator.infrastructure;

import com.enerlytics.simulator.config.SimulatorProperties;
import com.enerlytics.simulator.domain.TelemetryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class TelemetryEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SimulatorProperties properties;

    public TelemetryEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper,
                                  SimulatorProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public CompletableFuture<SendResult<String, String>> send(TelemetryEvent event) {
        try {
            String key = event.getOrganizationId().toString() + ":" + event.getMeterId().toString();
            String payload = objectMapper.writeValueAsString(event);
            log.trace("Producing telemetry event {} for meter {}", event.getEventId(), event.getMeterId());
            return kafkaTemplate.send(properties.getKafka().getTopic(), key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to produce event {}: {}", event.getEventId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize event {}", event.getEventId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
