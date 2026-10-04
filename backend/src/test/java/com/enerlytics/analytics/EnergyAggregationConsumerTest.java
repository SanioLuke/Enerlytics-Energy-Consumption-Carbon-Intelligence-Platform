package com.enerlytics.analytics;

import com.enerlytics.analytics.api.kafka.EnergyAggregationConsumer;
import com.enerlytics.analytics.application.EnergyAggregationService;
import com.enerlytics.telemetry.api.event.MeterReadingValidatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnergyAggregationConsumerTest {

    @Mock
    private EnergyAggregationService aggregationService;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private EnergyAggregationConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new EnergyAggregationConsumer(aggregationService, objectMapper);
    }

    @Test
    void validEventAggregatesBeforeAcknowledging() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-01-15T10:05:00Z");
        MeterReadingValidatedEvent event = new MeterReadingValidatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), meterId, orgId, UUID.randomUUID(), null, timestamp,
                BigDecimal.ONE, BigDecimal.TEN, null, null, BigDecimal.valueOf(0.95), null, "OK");

        consumer.consume(record(objectMapper.writeValueAsString(event)), acknowledgment);

        var order = inOrder(aggregationService, acknowledgment);
        order.verify(aggregationService).aggregateReading(orgId, meterId, timestamp);
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    void processingFailureDoesNotAcknowledge() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID meterId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-01-15T10:05:00Z");
        MeterReadingValidatedEvent event = new MeterReadingValidatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), meterId, orgId, UUID.randomUUID(), null, timestamp,
                BigDecimal.ONE, null, null, null, null, null, "OK");
        doThrow(new RuntimeException("database unavailable"))
                .when(aggregationService).aggregateReading(orgId, meterId, timestamp);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> consumer.consume(record(objectMapper.writeValueAsString(event)), acknowledgment))
                .isInstanceOf(RuntimeException.class);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void malformedPayloadIsAcknowledgedAsPoisonMessage() {
        consumer.consume(record("not-json"), acknowledgment);

        verifyNoInteractions(aggregationService);
        verify(acknowledgment).acknowledge();
    }

    private ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>("validated", 0, 1L, "key", payload);
    }
}
