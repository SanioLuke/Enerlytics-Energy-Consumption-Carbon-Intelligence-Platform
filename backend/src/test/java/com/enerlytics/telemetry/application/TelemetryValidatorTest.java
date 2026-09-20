package com.enerlytics.telemetry.application;

import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.telemetry.api.event.MeterReadingReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryValidatorTest {

    @Mock
    private MeterRepository meterRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), Clock.systemUTC().getZone());
    private TelemetryValidator validator;

    private static final Instant BASE_TIME = Instant.parse("2026-01-15T11:59:00Z");

    @BeforeEach
    void setUp() {
        validator = new TelemetryValidator(meterRepository, clock);
    }

    @Test
    void validEventReturnsValidResult() {
        MeterEntity meter = activeMeter();
        when(meterRepository.findById(meter.getId())).thenReturn(Optional.of(meter));

        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), BASE_TIME, BigDecimal.valueOf(1.5), "OK");

        TelemetryValidationResult result = validator.validate(event);

        assertThat(result).isInstanceOf(TelemetryValidationResult.Valid.class);
        assertThat(((TelemetryValidationResult.Valid) result).meter().getId()).isEqualTo(meter.getId());
    }

    @Test
    void unknownMeterIsRejected() {
        UUID meterId = UUID.randomUUID();
        when(meterRepository.findById(meterId)).thenReturn(Optional.empty());

        TelemetryValidationResult result = validator.validate(event(UUID.randomUUID(), meterId, BASE_TIME, BigDecimal.valueOf(1.5), "OK"));

        assertThat(result).isInstanceOf(TelemetryValidationResult.Rejected.class);
        assertThat(((TelemetryValidationResult.Rejected) result).reasonCode()).isEqualTo("UNKNOWN_METER");
    }

    @Test
    void inactiveMeterIsRejected() {
        MeterEntity meter = meterWithStatus(MeterStatus.PROVISIONING);
        when(meterRepository.findById(meter.getId())).thenReturn(Optional.of(meter));

        TelemetryValidationResult result = validator.validate(event(meter.getOrganization().getId(), meter.getId(), BASE_TIME, BigDecimal.valueOf(1.5), "OK"));

        assertThat(result).isInstanceOf(TelemetryValidationResult.Rejected.class);
        assertThat(((TelemetryValidationResult.Rejected) result).reasonCode()).isEqualTo("METER_NOT_ACTIVE");
    }

    @Test
    void tenantMismatchIsRejected() {
        MeterEntity meter = activeMeter();
        when(meterRepository.findById(meter.getId())).thenReturn(Optional.of(meter));

        MeterReadingReceivedEvent event = event(UUID.randomUUID(), meter.getId(), BASE_TIME, BigDecimal.valueOf(1.5), "OK");
        TelemetryValidationResult result = validator.validate(event);

        assertThat(result).isInstanceOf(TelemetryValidationResult.Rejected.class);
        assertThat(((TelemetryValidationResult.Rejected) result).reasonCode()).isEqualTo("TENANT_MISMATCH");
    }

    @Test
    void futureTimestampIsRejected() {
        MeterEntity meter = activeMeter();
        when(meterRepository.findById(meter.getId())).thenReturn(Optional.of(meter));

        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), Instant.parse("2026-01-15T12:10:00Z"), BigDecimal.valueOf(1.5), "OK");

        TelemetryValidationResult result = validator.validate(event);

        assertThat(result).isInstanceOf(TelemetryValidationResult.Rejected.class);
        assertThat(((TelemetryValidationResult.Rejected) result).reasonCode()).isEqualTo("FUTURE_TIMESTAMP");
    }

    @Test
    void negativeEnergyIsRejected() {
        MeterReadingReceivedEvent event = event(UUID.randomUUID(), UUID.randomUUID(), BASE_TIME, BigDecimal.valueOf(-1), "OK");

        TelemetryValidationResult result = validator.validate(event);

        assertThat(result).isInstanceOf(TelemetryValidationResult.Rejected.class);
        assertThat(((TelemetryValidationResult.Rejected) result).reasonCode()).isEqualTo("INVALID_ENERGY");
        verify(meterRepository, never()).findById(any());
    }

    @Test
    void missingQualityIsRejected() {
        MeterEntity meter = activeMeter();
        when(meterRepository.findById(meter.getId())).thenReturn(Optional.of(meter));

        MeterReadingReceivedEvent event = event(meter.getOrganization().getId(), meter.getId(), BASE_TIME, BigDecimal.valueOf(1.5), "MISSING");

        TelemetryValidationResult result = validator.validate(event);

        assertThat(result).isInstanceOf(TelemetryValidationResult.Rejected.class);
        assertThat(((TelemetryValidationResult.Rejected) result).reasonCode()).isEqualTo("MISSING_QUALITY");
    }

    private MeterEntity activeMeter() {
        return meterWithStatus(MeterStatus.ACTIVE);
    }

    private MeterEntity meterWithStatus(MeterStatus status) {
        OrganizationEntity org = new OrganizationEntity("org", "Org");
        setId(org, UUID.randomUUID());
        SiteEntity site = new SiteEntity(org, "S1", "Site", "UTC");
        setId(site, UUID.randomUUID());
        MeterEntity meter = new MeterEntity(org, site, "M-1", "Main", 60);
        setId(meter, UUID.randomUUID());
        meter.setStatus(status);
        return meter;
    }

    private MeterReadingReceivedEvent event(UUID organizationId, UUID meterId, Instant timestamp,
                                            BigDecimal energyKwh, String qualityStatus) {
        return new MeterReadingReceivedEvent(
                UUID.randomUUID(),
                "1.0",
                meterId,
                organizationId,
                UUID.randomUUID(),
                timestamp,
                energyKwh,
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(230),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(0.95),
                BigDecimal.valueOf(50),
                qualityStatus);
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
}
