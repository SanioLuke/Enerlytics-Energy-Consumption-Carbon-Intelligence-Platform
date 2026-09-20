package com.enerlytics.simulator.infrastructure;

import com.enerlytics.simulator.config.SimulatorProperties;
import com.enerlytics.simulator.domain.TelemetryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SimulatorProperties properties;

    @InjectMocks
    private TelemetryEventProducer producer;

    @Test
    void sendsEventWithCorrectKeyAndTopic() throws Exception {
        SimulatorProperties.KafkaProperties kafkaProps = new SimulatorProperties.KafkaProperties();
        kafkaProps.setTopic("test.topic");
        when(properties.getKafka()).thenReturn(kafkaProps);

        TelemetryEvent event = new TelemetryEvent();
        event.setEventId(UUID.randomUUID());
        event.setMeterId(UUID.randomUUID());
        event.setOrganizationId(UUID.randomUUID());
        event.setPowerFactor(null);

        when(objectMapper.writeValueAsString(event)).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        producer.send(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), anyString());
        assertThat(topicCaptor.getValue()).isEqualTo("test.topic");
        assertThat(keyCaptor.getValue()).isEqualTo(event.getOrganizationId() + ":" + event.getMeterId());
    }
}
