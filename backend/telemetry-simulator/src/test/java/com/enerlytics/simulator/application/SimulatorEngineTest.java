package com.enerlytics.simulator.application;

import com.enerlytics.simulator.config.SimulatorProperties;
import com.enerlytics.simulator.domain.SimulatedMeter;
import com.enerlytics.simulator.domain.SimulationProfile;
import com.enerlytics.simulator.domain.TelemetryEvent;
import com.enerlytics.simulator.infrastructure.MeterSource;
import com.enerlytics.simulator.infrastructure.TelemetryEventProducer;
import com.enerlytics.simulator.metrics.SimulatorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulatorEngineTest {

    @Mock
    private MeterSource meterSource;

    @Mock
    private TelemetryGenerator generator;

    @Mock
    private TelemetryEventProducer producer;

    private SimulatorProperties properties;
    private SimulatorMetrics metrics;
    private MutableTimeSource timeSource;

    @BeforeEach
    void setUp() {
        properties = new SimulatorProperties();
        properties.setEnabled(true);
        properties.setTickInterval(Duration.ofMillis(100));
        properties.setAcceleration(1.0);
        properties.setMeterRefreshInterval(Duration.ofMillis(50));
        metrics = new SimulatorMetrics(new SimpleMeterRegistry());
        timeSource = new MutableTimeSource(Instant.parse("2026-01-05T00:00:00Z"));
        lenient().when(producer.send(any(TelemetryEvent.class))).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void tickProducesExpectedReadingsForActiveMeter() {
        SimulatedMeter meter = new SimulatedMeter(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 60, SimulationProfile.OFFICE, "UTC");
        when(meterSource.fetchActiveSimulatedMeters()).thenReturn(List.of(meter));
        when(generator.generate(eq(meter), any(Instant.class), anyInt(), any(), anyDouble()))
                .thenAnswer(inv -> {
                    TelemetryEvent event = new TelemetryEvent();
                    event.setEventId(UUID.randomUUID());
                    return event;
                });
        when(producer.send(any(TelemetryEvent.class))).thenReturn(CompletableFuture.completedFuture(null));

        SimulatorEngine engine = new SimulatorEngine(properties, meterSource, generator, producer, metrics, timeSource);
        engine.tick(); // fetch + align

        timeSource.advance(Duration.ofMinutes(5));
        engine.tick();

        ArgumentCaptor<TelemetryEvent> captor = ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(producer, atLeast(1)).send(captor.capture());
        assertThat(captor.getAllValues()).isNotEmpty();
    }

    @Test
    void accelerationGeneratesMoreReadingsInSameRealDuration() {
        properties.setAcceleration(3600.0);

        SimulatedMeter meter = new SimulatedMeter(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 60, SimulationProfile.OFFICE, "UTC");
        when(meterSource.fetchActiveSimulatedMeters()).thenReturn(List.of(meter));
        when(generator.generate(eq(meter), any(Instant.class), anyInt(), any(), anyDouble()))
                .thenAnswer(inv -> {
                    TelemetryEvent event = new TelemetryEvent();
                    event.setEventId(UUID.randomUUID());
                    return event;
                });
        when(producer.send(any(TelemetryEvent.class))).thenReturn(CompletableFuture.completedFuture(null));

        SimulatorEngine engine = new SimulatorEngine(properties, meterSource, generator, producer, metrics, timeSource);
        engine.tick();

        timeSource.advance(Duration.ofSeconds(1));
        engine.tick();

        // 1 real second at 3600x = 1 virtual hour -> approximately 60 readings for a 60s interval meter
        verify(producer, atLeast(59)).send(any(TelemetryEvent.class));
    }

    @Test
    void noReadingsWhenNoActiveMeters() {
        when(meterSource.fetchActiveSimulatedMeters()).thenReturn(List.of());

        SimulatorEngine engine = new SimulatorEngine(properties, meterSource, generator, producer, metrics, timeSource);
        engine.tick();
        timeSource.advance(Duration.ofMinutes(5));
        engine.tick();

        verify(producer, never()).send(any());
    }

    private static class MutableTimeSource implements TimeSource {
        private Instant now;

        MutableTimeSource(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public Instant now() {
            return now;
        }
    }
}
