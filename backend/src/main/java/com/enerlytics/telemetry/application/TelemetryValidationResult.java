package com.enerlytics.telemetry.application;

import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.telemetry.api.event.MeterReadingReceivedEvent;

public sealed interface TelemetryValidationResult {

    record Valid(MeterReadingReceivedEvent event, MeterEntity meter) implements TelemetryValidationResult {
    }

    record Rejected(MeterReadingReceivedEvent event, String reasonCode, String reasonDetail) implements TelemetryValidationResult {
    }
}
