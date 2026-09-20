package com.enerlytics.telemetry.api.kafka;

import com.enerlytics.telemetry.api.event.MeterReadingReceivedEvent;
import com.enerlytics.telemetry.application.TelemetryIngestionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class TelemetryIngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionConsumer.class);

    private final TelemetryIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public TelemetryIngestionConsumer(TelemetryIngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${enerlytics.kafka.topics.meter-reading-received:enerlytics.telemetry.meter-reading-received.v1}",
            groupId = "${enerlytics.kafka.consumer.groups.ingestion:enerlytics-telemetry-ingestion}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            MeterReadingReceivedEvent event = objectMapper.readValue(record.value(), MeterReadingReceivedEvent.class);
            log.debug("Ingesting telemetry event {} for meter {}", event.eventId(), event.meterId());
            ingestionService.ingest(event);
            acknowledgment.acknowledge();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize telemetry record at offset {}: {}", record.offset(), e.getMessage());
            // Poison message: acknowledge to avoid blocking the partition.
            // A production runbook would route this to a DLT.
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Telemetry ingestion failed for record at offset {}: {}", record.offset(), e.getMessage());
            // Do not acknowledge; Kafka will redeliver according to consumer retry policy.
            throw e;
        }
    }
}
