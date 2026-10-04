package com.enerlytics.alert.application;

import com.enerlytics.alert.api.event.AlertAcknowledgedEvent;
import com.enerlytics.alert.api.event.AlertResolvedEvent;
import com.enerlytics.alert.domain.AlertInstanceEntity;
import com.enerlytics.alert.infrastructure.persistence.AlertInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AlertLifecycleService {

    private final AlertInstanceRepository alertInstanceRepository;
    private final AlertOutbox outbox;

    public AlertLifecycleService(AlertInstanceRepository alertInstanceRepository,
                                  AlertOutbox outbox) {
        this.alertInstanceRepository = alertInstanceRepository;
        this.outbox = outbox;
    }

    @Transactional
    public AlertInstanceEntity acknowledge(UUID organizationId, UUID alertId, String user) {
        AlertInstanceEntity entity = alertInstanceRepository.findByIdAndOrganizationId(alertId, organizationId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Alert not found"));
        Instant now = Instant.now();
        entity.acknowledge(user, now);
        AlertInstanceEntity saved = alertInstanceRepository.save(entity);
        outbox.enqueueAlertAcknowledged(organizationId, saved, now);
        return saved;
    }

    @Transactional
    public AlertInstanceEntity resolve(UUID organizationId, UUID alertId, String user) {
        AlertInstanceEntity entity = alertInstanceRepository.findByIdAndOrganizationId(alertId, organizationId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Alert not found"));
        Instant now = Instant.now();
        entity.resolve(user, now);
        AlertInstanceEntity saved = alertInstanceRepository.save(entity);
        outbox.enqueueAlertResolved(organizationId, saved, now);
        return saved;
    }
}
