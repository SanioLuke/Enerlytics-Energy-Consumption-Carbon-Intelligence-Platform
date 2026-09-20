package com.enerlytics.telemetry.application;

import com.enerlytics.telemetry.domain.OutboxEntity;
import com.enerlytics.telemetry.infrastructure.persistence.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!test")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int pageSize;

    public OutboxRelay(OutboxRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       @Value("${enerlytics.outbox.relay.page-size:100}") int pageSize) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.pageSize = pageSize;
    }

    @Scheduled(fixedDelayString = "${enerlytics.outbox.relay.interval:PT1S}")
    @Transactional
    public void publishUnsent() {
        List<OutboxEntity> unsent = outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, pageSize));
        if (unsent.isEmpty()) {
            return;
        }

        for (OutboxEntity outbox : unsent) {
            try {
                kafkaTemplate.send(outbox.getTopic(), outbox.getPartitionKey(), outbox.getPayload())
                        .get(5, TimeUnit.SECONDS);
                outbox.markPublished(Instant.now());
            } catch (Exception e) {
                log.warn("Failed to publish outbox record {} to topic {}: {}",
                        outbox.getId(), outbox.getTopic(), e.getMessage());
                // Leave unpublished; next poll retries.
                throw new RuntimeException("Outbox publish failed", e);
            }
        }
        outboxRepository.saveAll(unsent);
    }
}
