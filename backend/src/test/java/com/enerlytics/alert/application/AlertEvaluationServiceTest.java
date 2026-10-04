package com.enerlytics.alert.application;

import com.enerlytics.alert.api.dto.CreateAlertRuleRequest;
import com.enerlytics.alert.domain.AlertSeverity;
import com.enerlytics.alert.domain.AlertStatus;
import com.enerlytics.alert.domain.AlertType;
import com.enerlytics.alert.domain.ComparisonOperator;
import com.enerlytics.alert.infrastructure.persistence.AlertInstanceRepository;
import com.enerlytics.alert.infrastructure.persistence.AlertRuleRepository;
import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.config.JpaConfig;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import com.enerlytics.telemetry.infrastructure.persistence.OutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, AlertRuleService.class, AlertEvaluationService.class,
        AlertMetricsResolver.class, AlertOutbox.class,
        com.fasterxml.jackson.databind.ObjectMapper.class})
class AlertEvaluationServiceTest {

    private static final Instant HOUR = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private AlertRuleService ruleService;

    @Autowired
    private AlertEvaluationService evaluationService;

    @Autowired
    private AlertInstanceRepository alertInstanceRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private EnergyAggregateRepository energyAggregateRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
        alertInstanceRepository.deleteAll();
        alertRuleRepository.deleteAll();
        energyAggregateRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void highConsumptionRuleCreatesOpenAlert() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("alert-1", "Alert Org"));
        UUID meterId = UUID.randomUUID();
        insertEnergy(org.getId(), meterId, HOUR, new BigDecimal("100.000"));
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(
                "High consumption", AlertType.HIGH_CONSUMPTION.name(), DimensionType.METER, meterId,
                "ENERGY_CONSUMED_KWH", ComparisonOperator.GT.name(), new BigDecimal("50"),
                3600L, AlertSeverity.WARNING.name(), 3600L, true);
        ruleService.create(org.getId(), request);

        var created = evaluationService.evaluate(org.getId(), DimensionType.METER, meterId, HOUR);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(created.get(0).getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(created.get(0).getObservedValue()).isEqualByComparingTo("100.000");
        assertThat(created.get(0).getAlertType()).isEqualTo(AlertType.HIGH_CONSUMPTION);
        assertThat(created.get(0).getContextPayload()).contains("observedValue");
    }

    @Test
    void cooldownPreventsDuplicateAlertStorm() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("alert-2", "Cooldown Org"));
        UUID meterId = UUID.randomUUID();
        insertEnergy(org.getId(), meterId, HOUR, new BigDecimal("100.000"));
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(
                "High consumption", AlertType.HIGH_CONSUMPTION.name(), DimensionType.METER, meterId,
                "ENERGY_CONSUMED_KWH", ComparisonOperator.GT.name(), new BigDecimal("50"),
                3600L, AlertSeverity.WARNING.name(), 7200L, true);
        ruleService.create(org.getId(), request);

        evaluationService.evaluate(org.getId(), DimensionType.METER, meterId, HOUR);
        var second = evaluationService.evaluate(org.getId(), DimensionType.METER, meterId, HOUR.plus(1, ChronoUnit.HOURS));

        assertThat(alertInstanceRepository.count()).isEqualTo(1);
        assertThat(second).isEmpty();
    }

    @Test
    void ruleBelowThresholdDoesNotCreateAlert() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("alert-3", "Threshold Org"));
        UUID meterId = UUID.randomUUID();
        insertEnergy(org.getId(), meterId, HOUR, new BigDecimal("10.000"));
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(
                "High consumption", AlertType.HIGH_CONSUMPTION.name(), DimensionType.METER, meterId,
                "ENERGY_CONSUMED_KWH", ComparisonOperator.GT.name(), new BigDecimal("50"),
                3600L, AlertSeverity.WARNING.name(), 0L, true);
        ruleService.create(org.getId(), request);

        var created = evaluationService.evaluate(org.getId(), DimensionType.METER, meterId, HOUR);

        assertThat(created).isEmpty();
        assertThat(alertInstanceRepository.count()).isZero();
    }

    @Test
    void lifecycleAcknowledgeAndResolveEventsAreQueued() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("alert-4", "Lifecycle Org"));
        UUID meterId = UUID.randomUUID();
        insertEnergy(org.getId(), meterId, HOUR, new BigDecimal("100.000"));
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(
                "High demand", AlertType.HIGH_DEMAND.name(), DimensionType.METER, meterId,
                "PEAK_POWER_KW", ComparisonOperator.GTE.name(), new BigDecimal("5"),
                3600L, AlertSeverity.CRITICAL.name(), 0L, true);
        ruleService.create(org.getId(), request);
        var alert = evaluationService.evaluate(org.getId(), DimensionType.METER, meterId, HOUR).get(0);

        AlertLifecycleService lifecycle = new AlertLifecycleService(alertInstanceRepository, new AlertOutbox(outboxRepository, objectMapper()));
        lifecycle.acknowledge(org.getId(), alert.getId(), "operator-a");
        assertThat(alertInstanceRepository.findById(alert.getId()).get().getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(outboxRepository.count()).isEqualTo(2); // created + acknowledged

        lifecycle.resolve(org.getId(), alert.getId(), "operator-b");
        assertThat(alertInstanceRepository.findById(alert.getId()).get().getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(outboxRepository.count()).isEqualTo(3);
    }

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }

    private void insertEnergy(UUID orgId, UUID meterId, Instant timestamp, BigDecimal energy) {
        EnergyAggregateEntity aggregate = new EnergyAggregateEntity(orgId, DimensionType.METER, meterId,
                AggregationGranularity.HOUR, timestamp, timestamp.plus(1, ChronoUnit.HOURS));
        aggregate.applyMetrics(energy, BigDecimal.ZERO, energy, BigDecimal.ZERO, BigDecimal.ONE, 1, 0,
                new BigDecimal("100"), Instant.now());
        energyAggregateRepository.save(aggregate);
    }
}
