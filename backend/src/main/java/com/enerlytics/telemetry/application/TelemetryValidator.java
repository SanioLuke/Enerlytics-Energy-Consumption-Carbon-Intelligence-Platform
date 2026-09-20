package com.enerlytics.telemetry.application;

import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.telemetry.api.event.MeterReadingReceivedEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class TelemetryValidator {

    private static final Duration FUTURE_SKEW = Duration.ofMinutes(5);
    private static final Duration MAX_LATENESS = Duration.ofDays(7);
    private static final BigDecimal MIN_VOLTAGE = BigDecimal.valueOf(1);
    private static final BigDecimal MAX_VOLTAGE = BigDecimal.valueOf(1000);
    private static final BigDecimal MIN_FREQUENCY = BigDecimal.valueOf(45);
    private static final BigDecimal MAX_FREQUENCY = BigDecimal.valueOf(65);

    private final MeterRepository meterRepository;
    private final Clock clock;

    public TelemetryValidator(MeterRepository meterRepository, Clock clock) {
        this.meterRepository = meterRepository;
        this.clock = clock;
    }

    public TelemetryValidationResult validate(MeterReadingReceivedEvent event) {
        if (event == null) {
            return new TelemetryValidationResult.Rejected(null, "NULL_EVENT", "Received null event");
        }
        if (event.eventId() == null) {
            return reject(event, "MISSING_EVENT_ID", "eventId is required");
        }
        if (event.meterId() == null) {
            return reject(event, "MISSING_METER_ID", "meterId is required");
        }
        if (event.timestamp() == null) {
            return reject(event, "MISSING_TIMESTAMP", "timestamp is required");
        }
        if (event.energyKwh() == null || event.energyKwh().compareTo(BigDecimal.ZERO) < 0) {
            return reject(event, "INVALID_ENERGY", "energyKwh must be non-negative");
        }

        MeterEntity meter = meterRepository.findById(event.meterId()).orElse(null);
        if (meter == null) {
            return reject(event, "UNKNOWN_METER", "No meter found with id " + event.meterId());
        }
        if (meter.getStatus() != MeterStatus.ACTIVE) {
            return reject(event, "METER_NOT_ACTIVE", "Meter status is " + meter.getStatus());
        }
        if (event.organizationId() != null && !event.organizationId().equals(meter.getOrganization().getId())) {
            return reject(event, "TENANT_MISMATCH", "Event organizationId does not match meter organization");
        }

        Instant now = clock.instant();
        if (event.timestamp().isAfter(now.plus(FUTURE_SKEW))) {
            return reject(event, "FUTURE_TIMESTAMP", "timestamp is too far in the future");
        }
        if (event.timestamp().isBefore(now.minus(MAX_LATENESS))) {
            return reject(event, "STALE_TIMESTAMP", "timestamp is older than allowed lateness window");
        }

        if (event.powerKw() != null && event.powerKw().compareTo(BigDecimal.ZERO) < 0) {
            return reject(event, "INVALID_POWER", "powerKw must be non-negative");
        }
        if (event.voltage() != null && (event.voltage().compareTo(MIN_VOLTAGE) < 0 || event.voltage().compareTo(MAX_VOLTAGE) > 0)) {
            return reject(event, "INVALID_VOLTAGE", "voltage out of plausible range");
        }
        if (event.current() != null && event.current().compareTo(BigDecimal.ZERO) < 0) {
            return reject(event, "INVALID_CURRENT", "current must be non-negative");
        }
        if (event.powerFactor() != null && (event.powerFactor().compareTo(BigDecimal.ZERO) < 0 || event.powerFactor().compareTo(BigDecimal.ONE) > 0)) {
            return reject(event, "INVALID_POWER_FACTOR", "powerFactor must be between 0 and 1");
        }
        if (event.frequency() != null && (event.frequency().compareTo(MIN_FREQUENCY) < 0 || event.frequency().compareTo(MAX_FREQUENCY) > 0)) {
            return reject(event, "INVALID_FREQUENCY", "frequency out of plausible range");
        }
        if ("MISSING".equalsIgnoreCase(event.qualityStatus())) {
            return reject(event, "MISSING_QUALITY", "qualityStatus indicates missing reading");
        }

        return new TelemetryValidationResult.Valid(event, meter);
    }

    private TelemetryValidationResult.Rejected reject(MeterReadingReceivedEvent event, String code, String detail) {
        return new TelemetryValidationResult.Rejected(event, code, detail);
    }
}
