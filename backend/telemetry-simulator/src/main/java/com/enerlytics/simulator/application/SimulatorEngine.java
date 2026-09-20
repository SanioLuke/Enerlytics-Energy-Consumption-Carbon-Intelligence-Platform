package com.enerlytics.simulator.application;

import com.enerlytics.simulator.config.SimulatorProperties;
import com.enerlytics.simulator.domain.SimulatedMeter;
import com.enerlytics.simulator.domain.TelemetryEvent;
import com.enerlytics.simulator.infrastructure.MeterSource;
import com.enerlytics.simulator.infrastructure.TelemetryEventProducer;
import com.enerlytics.simulator.metrics.SimulatorMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class SimulatorEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulatorEngine.class);

    private final SimulatorProperties properties;
    private final MeterSource meterSource;
    private final TelemetryGenerator generator;
    private final TelemetryEventProducer producer;
    private final SimulatorMetrics metrics;
    private final TimeSource timeSource;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "telemetry-simulator");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentMap<UUID, MeterState> activeMeters = new ConcurrentHashMap<>();
    private volatile Instant startedAt;
    private volatile Instant virtualStart;
    private volatile Instant lastMeterRefresh = Instant.EPOCH;

    @Autowired
    public SimulatorEngine(SimulatorProperties properties,
                           MeterSource meterSource,
                           TelemetryGenerator generator,
                           TelemetryEventProducer producer,
                           SimulatorMetrics metrics) {
        this(properties, meterSource, generator, producer, metrics, Instant::now);
    }

    public SimulatorEngine(SimulatorProperties properties,
                           MeterSource meterSource,
                           TelemetryGenerator generator,
                           TelemetryEventProducer producer,
                           SimulatorMetrics metrics,
                           TimeSource timeSource) {
        this.properties = properties;
        this.meterSource = meterSource;
        this.generator = generator;
        this.producer = producer;
        this.metrics = metrics;
        this.timeSource = timeSource;
    }

    @PostConstruct
    public synchronized void start() {
        if (!properties.isEnabled()) {
            log.info("Telemetry simulator is disabled");
            return;
        }
        if (startedAt != null) {
            return;
        }
        startedAt = timeSource.now();
        virtualStart = startedAt;
        long tickMs = Math.max(100, properties.getTickInterval().toMillis());
        executor.scheduleAtFixedRate(this::tick, 0, tickMs, TimeUnit.MILLISECONDS);
        log.info("Telemetry simulator started with tick interval {} ms, acceleration {}", tickMs, properties.getAcceleration());
    }

    @PreDestroy
    public synchronized void stop() {
        if (startedAt == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        startedAt = null;
        log.info("Telemetry simulator stopped");
    }

    void tick() {
        try {
            Instant now = timeSource.now();
            if (startedAt == null) {
                startedAt = now;
                virtualStart = now;
            }
            refreshMetersIfNeeded(now);
            if (activeMeters.isEmpty()) {
                return;
            }

            Instant virtualNow = virtualStart.plus(elapsedSinceStart(now));
            for (Map.Entry<UUID, MeterState> entry : activeMeters.entrySet()) {
                MeterState state = entry.getValue();
                SimulatedMeter meter = state.meter;
                Duration interval = Duration.ofSeconds(meter.readingIntervalSeconds());

                if (state.nextReading == null) {
                    state.nextReading = alignToInterval(virtualNow, interval);
                }

                int sequence = state.sequence;
                while (!state.nextReading.isAfter(virtualNow)) {
                    TelemetryEvent event = generator.generate(meter, state.nextReading, sequence, properties.getSeed(), properties.getAnomalyProbability());
                    produce(event);
                    sequence++;
                    state.nextReading = state.nextReading.plus(interval);
                }
                state.sequence = sequence;
            }
        } catch (Exception e) {
            log.error("Simulator tick failed", e);
        }
    }

    private void refreshMetersIfNeeded(Instant now) {
        if (Duration.between(lastMeterRefresh, now).compareTo(properties.getMeterRefreshInterval()) < 0) {
            return;
        }
        List<SimulatedMeter> meters;
        try {
            meters = meterSource.fetchActiveSimulatedMeters();
        } catch (Exception e) {
            log.warn("Failed to refresh simulated meters: {}", e.getMessage());
            return;
        }
        lastMeterRefresh = now;

        // Remove meters no longer active
        activeMeters.keySet().retainAll(meters.stream().map(SimulatedMeter::meterId).toList());

        for (SimulatedMeter meter : meters) {
            activeMeters.computeIfAbsent(meter.meterId(), id -> new MeterState(meter));
        }
        metrics.setActiveMeters(activeMeters.size());
        log.debug("Refreshed simulated meters: {}", activeMeters.size());
    }

    private void produce(TelemetryEvent event) {
        producer.send(event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        metrics.recordFailed();
                    } else {
                        metrics.recordProduced();
                    }
                });
    }

    private Duration elapsedSinceStart(Instant now) {
        if (startedAt == null) {
            return Duration.ZERO;
        }
        long realNanos = Duration.between(startedAt, now).toNanos();
        long virtualNanos = (long) (realNanos * properties.getAcceleration());
        return Duration.ofNanos(virtualNanos);
    }

    private Instant alignToInterval(Instant virtualNow, Duration interval) {
        long seconds = interval.getSeconds();
        long epochSecond = virtualNow.getEpochSecond();
        long aligned = (epochSecond / seconds) * seconds;
        return Instant.ofEpochSecond(aligned);
    }

    private static class MeterState {
        final SimulatedMeter meter;
        Instant nextReading;
        int sequence;

        MeterState(SimulatedMeter meter) {
            this.meter = meter;
        }
    }
}
