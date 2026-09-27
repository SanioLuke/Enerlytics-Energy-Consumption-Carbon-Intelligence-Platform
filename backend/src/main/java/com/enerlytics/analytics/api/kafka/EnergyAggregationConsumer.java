package com.enerlytics.analytics.api.kafka;

import com.enerlytics.analytics.application.EnergyAggregationService;
import com.enerlytics.telemetry.api.event.MeterReadingValidatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "enerlytics.aggregation.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class EnergyAggregationConsumer {

    private static final Logger log = LoggerFactory.getLogger(EnergyAggregationConsumer.class);

    private final EnergyAggregationService aggregationService;
    private final ObjectMapper objectMapper;

    public EnergyAggregationConsumer(EnergyAggregationService aggregationService, ObjectMapper objectMapper) {
        this.aggregationService = aggregationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${enerlytics.kafka.topics.meter-reading-validated:enerlytics.telemetry.meter-reading-validated.v1}",
            groupId = "${enerlytics.kafka.consumer.groups.aggregation:enerlytics-energy-aggregation}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            MeterReadingValidatedEvent event = objectMapper.readValue(record.value(), MeterReadingValidatedEvent.class);
            if (event.readingId() == null || event.timestamp() == null) {
                log.warn("Dropping malformed validated event at offset {}", record.offset());
                acknowledgment.acknowledge();
                return;
            }
            aggregationService.aggregateReading(event.organizationId(), event.meterId(), event.timestamp());
            acknowledgment.acknowledge();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize validated event at offset {}: {}", record.offset(), e.getMessage());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Energy aggregation failed for record at offset {}: {}", record.offset(), e.getMessage());
            throw e;
        }
    }
}
