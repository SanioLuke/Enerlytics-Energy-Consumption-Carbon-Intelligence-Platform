package com.enerlytics.alert.application;

import com.enerlytics.alert.api.event.AlertCreatedEvent;
import com.enerlytics.alert.domain.*;
import com.enerlytics.alert.infrastructure.persistence.AlertInstanceRepository;
import com.enerlytics.alert.infrastructure.persistence.AlertRuleRepository;
import com.enerlytics.analytics.domain.DimensionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class AlertEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationService.class);
    private static final String ALERT_CREATED_TOPIC = "enerlytics.alert.alert-created.v1";

    private final AlertRuleRepository alertRuleRepository;
    private final AlertInstanceRepository alertInstanceRepository;
    private final AlertMetricsResolver metricsResolver;
    private final AlertOutbox outbox;
    private final ObjectMapper objectMapper;

    public AlertEvaluationService(AlertRuleRepository alertRuleRepository,
                                    AlertInstanceRepository alertInstanceRepository,
                                    AlertMetricsResolver metricsResolver,
                                    AlertOutbox outbox,
                                    ObjectMapper objectMapper) {
        this.alertRuleRepository = alertRuleRepository;
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        this.alertInstanceRepository = alertInstanceRepository;
        this.metricsResolver = metricsResolver;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates all enabled rules for a scope and evaluation time. A new alert is
     * created only when no matching OPEN or ACKNOWLEDGED alert exists inside the
     * rule's cooldown window, suppressing alert storms. Created alerts are
     * persisted and written to the outbox for downstream notification and audit.
     */
    @Transactional
    public List<AlertInstanceEntity> evaluate(UUID organizationId, DimensionType scopeType,
                                             UUID scopeId, Instant evaluationTime) {
        List<AlertRuleEntity> rules = alertRuleRepository.findByOrganization_IdAndEnabledTrue(organizationId)
                .stream().filter(r -> r.isApplicableTo(scopeType, scopeId)).toList();
        List<AlertInstanceEntity> created = new ArrayList<>();

        for (AlertRuleEntity rule : rules) {
            metricsResolver.resolve(organizationId, scopeType, scopeId, rule.getMetric(), evaluationTime)
                    .filter(reading -> isWithinWindow(rule, reading.bucketStart(), evaluationTime))
                    .filter(reading -> rule.getComparisonOperator().matches(reading.value(), rule.getThreshold()))
                    .ifPresent(reading -> {
                        Instant cooldownCutoff = evaluationTime.minus(Duration.ofSeconds(rule.getCooldownSeconds()));
                        boolean active = alertInstanceRepository.findRecentActiveByRuleAndScope(
                                organizationId, rule.getId(), scopeId, cooldownCutoff).isEmpty();
                        if (active) {
                            AlertInstanceEntity instance = createInstance(rule, reading, evaluationTime, scopeType, scopeId);
                            alertInstanceRepository.save(instance);
                            outbox.enqueueAlertCreated(organizationId, instance);
                            created.add(instance);
                        }
                    });
        }
        return created;
    }

    /**
     * Evaluates a single rule at a time. Useful for manual trigger or replay APIs.
     */
    @Transactional
    public Optional<AlertInstanceEntity> evaluateRule(UUID organizationId, UUID ruleId, Instant evaluationTime) {
        AlertRuleEntity rule = alertRuleRepository.findByIdAndOrganization_Id(ruleId, organizationId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Alert rule not found"));
        if (!rule.isEnabled()) {
            throw new IllegalStateException("Alert rule is disabled");
        }
        return metricsResolver.resolve(organizationId, rule.getScopeType(), rule.getScopeId(),
                        rule.getMetric(), evaluationTime)
                .filter(reading -> rule.getComparisonOperator().matches(reading.value(), rule.getThreshold()))
                .map(reading -> {
                    Instant cooldownCutoff = evaluationTime.minus(Duration.ofSeconds(rule.getCooldownSeconds()));
                    if (!alertInstanceRepository.findRecentActiveByRuleAndScope(
                            organizationId, rule.getId(), rule.getScopeId(), cooldownCutoff).isEmpty()) {
                        throw new IllegalStateException("Active alert exists within cooldown window");
                    }
                    AlertInstanceEntity instance = createInstance(rule, reading, evaluationTime,
                            rule.getScopeType(), rule.getScopeId());
                    alertInstanceRepository.save(instance);
                    outbox.enqueueAlertCreated(organizationId, instance);
                    return instance;
                });
    }

    private boolean isWithinWindow(AlertRuleEntity rule, Instant metricBucket, Instant evaluationTime) {
        Duration window = Duration.ofSeconds(rule.getEvaluationWindowSeconds());
        return !metricBucket.isBefore(evaluationTime.minus(window))
                && !metricBucket.isAfter(evaluationTime);
    }

    private AlertInstanceEntity createInstance(AlertRuleEntity rule,
                                              AlertMetricsResolver.MetricReading reading,
                                              Instant triggeredAt,
                                              DimensionType scopeType,
                                              UUID scopeId) {
        String context;
        try {
            context = objectMapper.writeValueAsString(Map.of(
                    "metric", reading.metric(),
                    "observedValue", reading.value().toPlainString(),
                    "threshold", rule.getThreshold().toPlainString(),
                    "operator", rule.getComparisonOperator().name(),
                    "evaluationTime", triggeredAt.toString()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize alert context", e);
        }
        String dedupKey = rule.getId() + ":" + scopeType + ":" + scopeId + ":"
                + reading.bucketStart().toString() + ":" + reading.metric();
        return new AlertInstanceEntity(rule.getOrganization().getId(), rule, scopeType, scopeId,
                rule.getAlertType(), reading.metric(), reading.value(), rule.getThreshold(),
                rule.getSeverity(), triggeredAt, context, dedupKey);
    }

    public static AlertCreatedEvent toEvent(AlertInstanceEntity entity, Instant occurredAt) {
        return new AlertCreatedEvent(UUID.randomUUID(), occurredAt, entity.getOrganizationId(),
                entity.getId(), entity.getRule().getId(), entity.getAlertType().name(),
                entity.getSeverity().name(), entity.getScopeId(), entity.getScopeType().name(),
                entity.getMetric(), entity.getObservedValue(), entity.getThreshold(),
                entity.getTriggeredAt(), entity.getContextPayload());
    }
}
