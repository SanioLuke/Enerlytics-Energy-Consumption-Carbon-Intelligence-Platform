package com.enerlytics.telemetry.api.kafka;

import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import com.enerlytics.telemetry.api.event.MeterReadingReceivedEvent;
import com.enerlytics.telemetry.application.TelemetryIngestionService;
import com.enerlytics.telemetry.domain.MeterReadingEntity;
import com.enerlytics.telemetry.domain.MeterReadingRejectedEntity;
import com.enerlytics.telemetry.domain.OutboxEntity;
import com.enerlytics.telemetry.infrastructure.persistence.MeterReadingRejectedRepository;
import com.enerlytics.telemetry.infrastructure.persistence.MeterReadingRepository;
import com.enerlytics.telemetry.infrastructure.persistence.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("integration")
@EmbeddedKafka(
        topics = {
                "enerlytics.telemetry.meter-reading-received.v1",
                "enerlytics.telemetry.meter-reading-validated.v1",
                "enerlytics.telemetry.meter-reading-rejected.v1"
        },
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class TelemetryIngestionKafkaIntegrationTest {

    private static final String RECEIVED_TOPIC = "enerlytics.telemetry.meter-reading-received.v1";
    private static final String VALIDATED_TOPIC = "enerlytics.telemetry.meter-reading-validated.v1";
    private static final String REJECTED_TOPIC = "enerlytics.telemetry.meter-reading-rejected.v1";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MeterReadingRepository readingRepository;

    @Autowired
    private MeterReadingRejectedRepository rejectedRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private KafkaTemplate<String, String> spyKafkaTemplate;

    @MockitoSpyBean
    private TelemetryIngestionService spyIngestionService;

    @BeforeEach
    void cleanDatabase() {
        readingRepository.deleteAll();
        rejectedRepository.deleteAll();
        outboxRepository.deleteAll();
        meterRepository.deleteAll();
        siteRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void validTelemetryPersistsReadingAndValidatedOutbox() throws Exception {
        MeterEntity meter = createActiveMeter("M-VALID");

        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(2.5), "OK");
        kafkaTemplate.send(RECEIVED_TOPIC, key(meter), objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(readingRepository.findAll()).hasSize(1);
            MeterReadingEntity reading = readingRepository.findAll().get(0);
            assertThat(reading.getSourceEventId()).isEqualTo(event.eventId());
            assertThat(reading.getMeterId()).isEqualTo(meter.getId());
            assertThat(reading.getEnergyKwh()).isEqualByComparingTo("2.5");
        });

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<OutboxEntity> outbox = outboxRepository.findAll();
            assertThat(outbox).hasSize(1);
            assertThat(outbox.get(0).getTopic()).isEqualTo(VALIDATED_TOPIC);
            assertThat(outbox.get(0).getPublishedAt()).isNotNull();
        });
    }

    @Test
    void invalidJsonIsAcknowledgedAndIgnored() throws Exception {
        kafkaTemplate.send(RECEIVED_TOPIC, "key", "not-json").get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(readingRepository.findAll()).isEmpty();
            assertThat(rejectedRepository.findAll()).isEmpty();
        });
    }

    @Test
    void missingMeterIdIsRejected() throws Exception {
        MeterReadingReceivedEvent event = event(UUID.randomUUID(), null, Instant.now(), BigDecimal.valueOf(1.5), "OK");
        kafkaTemplate.send(RECEIVED_TOPIC, "key", objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rejectedRepository.findAll()).hasSize(1);
            MeterReadingRejectedEntity rejected = rejectedRepository.findAll().get(0);
            assertThat(rejected.getReasonCode()).isEqualTo("MISSING_METER_ID");
        });

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(outboxRepository.findAll()).hasSize(1);
            assertThat(outboxRepository.findAll().get(0).getTopic()).isEqualTo(REJECTED_TOPIC);
        });
    }

    @Test
    void unknownMeterIsRejected() throws Exception {
        MeterReadingReceivedEvent event = event(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), BigDecimal.valueOf(1.5), "OK");
        kafkaTemplate.send(RECEIVED_TOPIC, "key", objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rejectedRepository.findAll()).hasSize(1);
            assertThat(rejectedRepository.findAll().get(0).getReasonCode()).isEqualTo("UNKNOWN_METER");
        });
    }

    @Test
    void inactiveMeterIsRejected() throws Exception {
        MeterEntity meter = createMeterWithStatus(MeterStatus.PROVISIONING);

        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(1.5), "OK");
        kafkaTemplate.send(RECEIVED_TOPIC, key(meter), objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rejectedRepository.findAll()).hasSize(1);
            assertThat(rejectedRepository.findAll().get(0).getReasonCode()).isEqualTo("METER_NOT_ACTIVE");
        });
    }

    @Test
    void tenantMismatchIsRejected() throws Exception {
        MeterEntity meter = createActiveMeter("M-TENANT");
        UUID wrongOrgId = UUID.randomUUID();

        MeterReadingReceivedEvent event = event(wrongOrgId, meter.getId(), Instant.now(), BigDecimal.valueOf(1.5), "OK");
        kafkaTemplate.send(RECEIVED_TOPIC, wrongOrgId + ":" + meter.getId(), objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rejectedRepository.findAll()).hasSize(1);
            assertThat(rejectedRepository.findAll().get(0).getReasonCode()).isEqualTo("TENANT_MISMATCH");
        });
    }

    @Test
    void duplicateSourceEventIdIsIdempotent() throws Exception {
        MeterEntity meter = createActiveMeter("M-DUP");
        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(3.0), "OK");
        String payload = objectMapper.writeValueAsString(event);
        String key = key(meter);

        kafkaTemplate.send(RECEIVED_TOPIC, key, payload).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(readingRepository.findAll()).hasSize(1));

        kafkaTemplate.send(RECEIVED_TOPIC, key, payload).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(readingRepository.findAll()).hasSize(1));
        assertThat(rejectedRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll()).hasSize(1);
    }

    @Test
    void futureTimestampIsRejected() throws Exception {
        MeterEntity meter = createActiveMeter("M-FUTURE");
        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now().plus(Duration.ofHours(1)), BigDecimal.valueOf(1.5), "OK");
        kafkaTemplate.send(RECEIVED_TOPIC, key(meter), objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rejectedRepository.findAll()).hasSize(1);
            assertThat(rejectedRepository.findAll().get(0).getReasonCode()).isEqualTo("FUTURE_TIMESTAMP");
        });
    }

    @Test
    void invalidPowerIsRejected() throws Exception {
        MeterEntity meter = createActiveMeter("M-POWER");
        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(1.5), "OK", BigDecimal.valueOf(-1));
        kafkaTemplate.send(RECEIVED_TOPIC, key(meter), objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rejectedRepository.findAll()).hasSize(1);
            assertThat(rejectedRepository.findAll().get(0).getReasonCode()).isEqualTo("INVALID_POWER");
        });
    }

    @Test
    void invalidVoltageIsRejected() throws Exception {
        MeterEntity meter = createActiveMeter("M-VOLT");
        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(1.5), "OK", null, BigDecimal.valueOf(0));
        kafkaTemplate.send(RECEIVED_TOPIC, key(meter), objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rejectedRepository.findAll()).hasSize(1);
            assertThat(rejectedRepository.findAll().get(0).getReasonCode()).isEqualTo("INVALID_VOLTAGE");
        });
    }

    @Test
    void invalidFrequencyIsRejected() throws Exception {
        MeterEntity meter = createActiveMeter("M-FREQ");
        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(1.5), "OK", null, null, BigDecimal.valueOf(0));
        kafkaTemplate.send(RECEIVED_TOPIC, key(meter), objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rejectedRepository.findAll()).hasSize(1);
            assertThat(rejectedRepository.findAll().get(0).getReasonCode()).isEqualTo("INVALID_FREQUENCY");
        });
    }

    @Test
    void invalidPowerFactorIsRejected() throws Exception {
        MeterEntity meter = createActiveMeter("M-PF");
        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(1.5), "OK", null, null, null, BigDecimal.valueOf(1.5));
        kafkaTemplate.send(RECEIVED_TOPIC, key(meter), objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rejectedRepository.findAll()).hasSize(1);
            assertThat(rejectedRepository.findAll().get(0).getReasonCode()).isEqualTo("INVALID_POWER_FACTOR");
        });
    }

    @Test
    void databaseFailureTriggersKafkaRedeliveryAndEventualPersistence() throws Exception {
        MeterEntity meter = createActiveMeter("M-DBFAIL");
        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(4.0), "OK");
        String payload = objectMapper.writeValueAsString(event);
        String key = key(meter);

        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(inv -> {
            if (callCount.getAndIncrement() == 0) {
                throw new RuntimeException("simulated database failure");
            }
            return inv.callRealMethod();
        }).when(spyIngestionService).ingest(any(MeterReadingReceivedEvent.class));

        kafkaTemplate.send(RECEIVED_TOPIC, key, payload).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(callCount.get()).isGreaterThanOrEqualTo(2);
            assertThat(readingRepository.findAll()).hasSize(1);
            assertThat(readingRepository.findAll().get(0).getSourceEventId()).isEqualTo(event.eventId());
        });
    }

    @Test
    void outboxPublishFailureLeavesRecordUnpublishedUntilRetry() throws Exception {
        MeterEntity meter = createActiveMeter("M-OUTBOX");
        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(1.0), "OK");

        doThrow(new RuntimeException("simulated broker unavailable"))
                .when(spyKafkaTemplate).send(eq(VALIDATED_TOPIC), anyString(), anyString());

        kafkaTemplate.send(RECEIVED_TOPIC, key(meter), objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(readingRepository.findAll()).hasSize(1);
            assertThat(outboxRepository.findAll()).hasSize(1);
        });

        // The outbox record remains unpublished while the simulated broker is down.
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(outboxRepository.findAll().get(0).getPublishedAt()).isNull();
        });

        org.mockito.Mockito.reset(spyKafkaTemplate);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(outboxRepository.findAll().get(0).getPublishedAt()).isNotNull();
        });
    }

    @Test
    void consumerOffsetIsNotCommittedForFailedTransaction() throws Exception {
        MeterEntity meter = createActiveMeter("M-OFFSET");
        MeterReadingReceivedEvent first = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(1.0), "OK");
        MeterReadingReceivedEvent second = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(2.0), "OK");
        String key = key(meter);

        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(inv -> {
            if (callCount.getAndIncrement() == 0) {
                throw new RuntimeException("simulated database failure");
            }
            return inv.callRealMethod();
        }).when(spyIngestionService).ingest(any(MeterReadingReceivedEvent.class));

        kafkaTemplate.send(RECEIVED_TOPIC, key, objectMapper.writeValueAsString(first)).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(RECEIVED_TOPIC, key, objectMapper.writeValueAsString(second)).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(readingRepository.findAll()).hasSize(2);
            assertThat(callCount.get()).isGreaterThanOrEqualTo(3);
        });
    }

    private MeterEntity createActiveMeter(String code) {
        return createMeterWithStatus(code, MeterStatus.ACTIVE);
    }

    private MeterEntity createMeterWithStatus(MeterStatus status) {
        return createMeterWithStatus("M-" + UUID.randomUUID().toString().substring(0, 8), status);
    }

    private MeterEntity createMeterWithStatus(String code, MeterStatus status) {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("org-" + code, "Org " + code));
        SiteEntity site = siteRepository.save(new SiteEntity(org, "S-" + code, "Site " + code, "UTC"));
        MeterEntity meter = new MeterEntity(org, site, code, "Meter " + code, 60);
        meter.setStatus(status);
        return meterRepository.save(meter);
    }

    private String key(MeterEntity meter) {
        return meter.getOrganization().getId() + ":" + meter.getId();
    }

    private MeterReadingReceivedEvent event(UUID organizationId, UUID meterId, Instant timestamp,
                                            BigDecimal energyKwh, String qualityStatus) {
        return event(organizationId, meterId, timestamp, energyKwh, qualityStatus, BigDecimal.valueOf(90), BigDecimal.valueOf(230), BigDecimal.valueOf(50), BigDecimal.valueOf(0.95));
    }

    private MeterReadingReceivedEvent event(UUID organizationId, UUID meterId, Instant timestamp,
                                            BigDecimal energyKwh, String qualityStatus, BigDecimal powerKw) {
        return event(organizationId, meterId, timestamp, energyKwh, qualityStatus, powerKw, BigDecimal.valueOf(230), BigDecimal.valueOf(50), BigDecimal.valueOf(0.95));
    }

    private MeterReadingReceivedEvent event(UUID organizationId, UUID meterId, Instant timestamp,
                                            BigDecimal energyKwh, String qualityStatus,
                                            BigDecimal powerKw, BigDecimal voltage) {
        return event(organizationId, meterId, timestamp, energyKwh, qualityStatus, powerKw, voltage, BigDecimal.valueOf(50), BigDecimal.valueOf(0.95));
    }

    private MeterReadingReceivedEvent event(UUID organizationId, UUID meterId, Instant timestamp,
                                            BigDecimal energyKwh, String qualityStatus,
                                            BigDecimal powerKw, BigDecimal voltage, BigDecimal frequency) {
        return event(organizationId, meterId, timestamp, energyKwh, qualityStatus, powerKw, voltage, frequency, BigDecimal.valueOf(0.95));
    }

    private MeterReadingReceivedEvent event(UUID organizationId, UUID meterId, Instant timestamp,
                                            BigDecimal energyKwh, String qualityStatus,
                                            BigDecimal powerKw, BigDecimal voltage, BigDecimal frequency, BigDecimal powerFactor) {
        return new MeterReadingReceivedEvent(
                UUID.randomUUID(), "1.0", meterId, organizationId, UUID.randomUUID(),
                timestamp, energyKwh, powerKw, voltage, BigDecimal.valueOf(10), powerFactor, frequency, qualityStatus);
    }
}
