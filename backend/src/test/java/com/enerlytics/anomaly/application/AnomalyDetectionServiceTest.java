package com.enerlytics.anomaly.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.anomaly.api.dto.AnomalyResponse;
import com.enerlytics.anomaly.config.AnomalyConfig;
import com.enerlytics.anomaly.detection.PercentageDeviationDetector;
import com.enerlytics.anomaly.detection.RollingMeanDeviationDetector;
import com.enerlytics.anomaly.detection.RollingZScoreDetector;
import com.enerlytics.anomaly.detection.SameHourBaselineDetector;
import com.enerlytics.anomaly.domain.MaintenanceWindowEntity;
import com.enerlytics.anomaly.infrastructure.persistence.DetectedAnomalyRepository;
import com.enerlytics.anomaly.infrastructure.persistence.MaintenanceWindowRepository;
import com.enerlytics.config.JpaConfig;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
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
@Import({JpaConfig.class, AnomalyConfig.class, AnomalyDetectionService.class,
        RollingMeanDeviationDetector.class, RollingZScoreDetector.class,
        SameHourBaselineDetector.class, PercentageDeviationDetector.class})
class AnomalyDetectionServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private AnomalyDetectionService service;

    @Autowired
    private EnergyAggregateRepository energyRepository;

    @Autowired
    private DetectedAnomalyRepository anomalyRepository;

    @Autowired
    private MaintenanceWindowRepository maintenanceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @AfterEach
    void cleanUp() {
        anomalyRepository.deleteAll();
        maintenanceRepository.deleteAll();
        energyRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void detectsAndPersistsInterpretableAnomaliesAfterStartup() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("anom-1", "Anomaly Org"));
        UUID meterId = UUID.randomUUID();
        for (int i = 0; i < 72; i++) {
            insert(org.getId(), meterId, START.plus(i, ChronoUnit.HOURS), "10.000", "100");
        }
        Instant anomalyAt = START.plus(72, ChronoUnit.HOURS);
        insert(org.getId(), meterId, anomalyAt, "30.000", "100");

        List<AnomalyResponse> result = service.detect(org.getId(), DimensionType.METER, meterId,
                anomalyAt, anomalyAt.plus(1, ChronoUnit.HOURS));

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(a -> {
            assertThat(a.entityType()).isEqualTo(DimensionType.METER);
            assertThat(a.entityId()).isEqualTo(meterId);
            assertThat(a.timestamp()).isEqualTo(anomalyAt);
            assertThat(a.actualConsumptionKwh()).isEqualByComparingTo("30.000");
            assertThat(a.expectedConsumptionKwh()).isPositive();
            assertThat(a.deviationPercentage()).isPositive();
            assertThat(a.confidence()).isBetween(new BigDecimal("0.0000"), new BigDecimal("1.0000"));
            assertThat(a.explanation()).contains("Actual");
        });
        assertThat(anomalyRepository.count()).isEqualTo(result.size());
    }

    @Test
    void incompleteTelemetryDoesNotGenerateAnomaly() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("anom-2", "Missing Org"));
        UUID meterId = UUID.randomUUID();
        for (int i = 0; i < 72; i++) {
            insert(org.getId(), meterId, START.plus(i, ChronoUnit.HOURS), "10.000", "100");
        }
        Instant target = START.plus(72, ChronoUnit.HOURS);
        insert(org.getId(), meterId, target, "100.000", "20");

        List<AnomalyResponse> result = service.detect(org.getId(), DimensionType.METER, meterId,
                target, target.plus(1, ChronoUnit.HOURS));

        assertThat(result).isEmpty();
        assertThat(anomalyRepository.count()).isZero();
    }

    @Test
    void startupPeriodSuppressesCandidate() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("anom-3", "Startup Org"));
        UUID meterId = UUID.randomUUID();
        for (int i = 0; i < 24; i++) {
            insert(org.getId(), meterId, START.plus(i, ChronoUnit.HOURS), "10.000", "100");
        }
        Instant target = START.plus(24, ChronoUnit.HOURS);
        insert(org.getId(), meterId, target, "100.000", "100");

        List<AnomalyResponse> result = service.detect(org.getId(), DimensionType.METER, meterId,
                target, target.plus(1, ChronoUnit.HOURS));

        assertThat(result).isEmpty();
        assertThat(anomalyRepository.count()).isZero();
    }

    @Test
    void maintenancePeriodIsSuppressedFromHistory() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("anom-4", "Maintenance Org"));
        UUID meterId = UUID.randomUUID();
        for (int i = 0; i < 72; i++) {
            insert(org.getId(), meterId, START.plus(i, ChronoUnit.HOURS), "10.000", "100");
        }
        Instant target = START.plus(72, ChronoUnit.HOURS);
        insert(org.getId(), meterId, target, "100.000", "100");
        maintenanceRepository.save(new MaintenanceWindowEntity(org, DimensionType.METER, meterId,
                target, target.plus(2, ChronoUnit.HOURS), "Planned service", "ops@example.com"));

        List<AnomalyResponse> detected = service.detect(org.getId(), DimensionType.METER, meterId,
                target, target.plus(1, ChronoUnit.HOURS));
        List<AnomalyResponse> history = service.history(org.getId(), DimensionType.METER, meterId,
                target, target.plus(1, ChronoUnit.HOURS));

        assertThat(detected).isEmpty();
        assertThat(history).isEmpty();
        assertThat(anomalyRepository.findAll()).isNotEmpty()
                .allSatisfy(a -> {
                    assertThat(a.isSuppressed()).isTrue();
                    assertThat(a.getSuppressionReason()).isEqualTo("MAINTENANCE_PERIOD");
                });
    }

    @Test
    void recalculationReplacesCanonicalAnomalies() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("anom-5", "Correction Org"));
        UUID meterId = UUID.randomUUID();
        for (int i = 0; i < 72; i++) {
            insert(org.getId(), meterId, START.plus(i, ChronoUnit.HOURS), "10.000", "100");
        }
        Instant target = START.plus(72, ChronoUnit.HOURS);
        EnergyAggregateEntity spike = insert(org.getId(), meterId, target, "100.000", "100");
        service.detect(org.getId(), DimensionType.METER, meterId, target, target.plus(1, ChronoUnit.HOURS));
        assertThat(anomalyRepository.count()).isPositive();

        spike.applyMetrics(new BigDecimal("10.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, 1, 0, new BigDecimal("100"), Instant.now());
        energyRepository.save(spike);
        List<AnomalyResponse> corrected = service.detect(org.getId(), DimensionType.METER, meterId,
                target, target.plus(1, ChronoUnit.HOURS));

        assertThat(corrected).isEmpty();
        assertThat(anomalyRepository.count()).isZero();
    }

    private EnergyAggregateEntity insert(UUID orgId, UUID meterId, Instant timestamp,
                                         String energy, String completeness) {
        EnergyAggregateEntity aggregate = new EnergyAggregateEntity(orgId, DimensionType.METER, meterId,
                AggregationGranularity.HOUR, timestamp, timestamp.plus(1, ChronoUnit.HOURS));
        aggregate.applyMetrics(new BigDecimal(energy), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, 1, 0, new BigDecimal(completeness), Instant.now());
        return energyRepository.save(aggregate);
    }
}
