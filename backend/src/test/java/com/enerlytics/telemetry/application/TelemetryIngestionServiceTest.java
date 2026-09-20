package com.enerlytics.telemetry.application;

import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.telemetry.api.event.MeterReadingReceivedEvent;
import com.enerlytics.telemetry.domain.MeterReadingEntity;
import com.enerlytics.telemetry.domain.MeterReadingRejectedEntity;
import com.enerlytics.telemetry.domain.OutboxEntity;
import com.enerlytics.telemetry.infrastructure.persistence.MeterReadingRejectedRepository;
import com.enerlytics.telemetry.infrastructure.persistence.MeterReadingRepository;
import com.enerlytics.telemetry.infrastructure.persistence.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceTest {

    @Mock
    private TelemetryValidator validator;

    @Mock
    private MeterReadingRepository readingRepository;

    @Mock
    private MeterReadingRejectedRepository rejectedRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private TelemetryIngestionService ingestionService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        ingestionService = new TelemetryIngestionService(
                validator, readingRepository, rejectedRepository, outboxRepository, objectMapper,
                "enerlytics.telemetry.meter-reading-validated.v1",
                "enerlytics.telemetry.meter-reading-rejected.v1");
    }

    @Test
    void validEventPersistsReadingAndOutbox() {
        MeterEntity meter = activeMeter();
        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.now(), BigDecimal.valueOf(2.5), "OK");
        when(validator.validate(event)).thenReturn(new TelemetryValidationResult.Valid(event, meter));
        when(readingRepository.save(any(MeterReadingEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(OutboxEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ingestionService.ingest(event);

        ArgumentCaptor<MeterReadingEntity> readingCaptor = ArgumentCaptor.forClass(MeterReadingEntity.class);
        verify(readingRepository).save(readingCaptor.capture());
        assertThat(readingCaptor.getValue().getMeterId()).isEqualTo(meter.getId());

        ArgumentCaptor<OutboxEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo("enerlytics.telemetry.meter-reading-validated.v1");
        assertThat(outboxCaptor.getValue().getPartitionKey()).contains(meter.getId().toString());
    }

    @Test
    void rejectedEventPersistsRejectionAndOutbox() {
        UUID meterId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        MeterReadingReceivedEvent event = event(orgId, meterId, Instant.now(), BigDecimal.valueOf(-1), "OK");
        when(validator.validate(event)).thenReturn(new TelemetryValidationResult.Rejected(event, "INVALID_ENERGY", "negative energy"));
        when(rejectedRepository.save(any(MeterReadingRejectedEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(OutboxEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ingestionService.ingest(event);

        ArgumentCaptor<MeterReadingRejectedEntity> rejectedCaptor = ArgumentCaptor.forClass(MeterReadingRejectedEntity.class);
        verify(rejectedRepository).save(rejectedCaptor.capture());
        assertThat(rejectedCaptor.getValue().getReasonCode()).isEqualTo("INVALID_ENERGY");

        ArgumentCaptor<OutboxEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo("enerlytics.telemetry.meter-reading-rejected.v1");
    }

    @Test
    void duplicateEventIsIgnored() {
        MeterReadingReceivedEvent event = event(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), BigDecimal.valueOf(1), "OK");
        when(readingRepository.existsBySourceEventId(event.eventId())).thenReturn(true);

        ingestionService.ingest(event);

        verify(validator, never()).validate(any());
        verify(readingRepository, never()).save(any());
        verify(rejectedRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    private MeterEntity activeMeter() {
        com.enerlytics.organization.domain.OrganizationEntity org = new com.enerlytics.organization.domain.OrganizationEntity("org", "Org");
        setId(org, UUID.randomUUID());
        SiteEntity site = new SiteEntity(org, "S1", "Site", "UTC");
        setId(site, UUID.randomUUID());
        MeterEntity meter = new MeterEntity(org, site, "M-1", "Main", 60);
        meter.setStatus(MeterStatus.ACTIVE);
        setId(meter, UUID.randomUUID());
        return meter;
    }

    private static void setId(Object target, UUID id) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(target, id);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new RuntimeException("id field not found");
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private MeterReadingReceivedEvent event(UUID organizationId, UUID meterId, Instant timestamp,
                                            BigDecimal energyKwh, String qualityStatus) {
        return new MeterReadingReceivedEvent(
                UUID.randomUUID(), "1.0", meterId, organizationId, UUID.randomUUID(),
                timestamp, energyKwh, BigDecimal.valueOf(90), BigDecimal.valueOf(230),
                BigDecimal.valueOf(10), BigDecimal.valueOf(0.95), BigDecimal.valueOf(50), qualityStatus);
    }
}
