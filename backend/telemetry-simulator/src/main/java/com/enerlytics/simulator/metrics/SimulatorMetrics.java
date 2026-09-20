package com.enerlytics.simulator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SimulatorMetrics {

    private final Counter producedCounter;
    private final Counter failedCounter;
    private final AtomicInteger activeMeters = new AtomicInteger(0);

    public SimulatorMetrics(MeterRegistry meterRegistry) {
        this.producedCounter = Counter.builder("telemetry.simulator.produced")
                .description("Telemetry events produced")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("telemetry.simulator.failed")
                .description("Telemetry events that failed to produce")
                .register(meterRegistry);
        Gauge.builder("telemetry.simulator.active_meters", activeMeters, AtomicInteger::get)
                .description("Number of active simulated meters")
                .register(meterRegistry);
    }

    public void recordProduced() {
        producedCounter.increment();
    }

    public void recordFailed() {
        failedCounter.increment();
    }

    public void setActiveMeters(int count) {
        activeMeters.set(count);
    }
}
