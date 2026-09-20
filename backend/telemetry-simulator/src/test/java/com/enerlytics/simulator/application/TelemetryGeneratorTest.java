package com.enerlytics.simulator.application;

import com.enerlytics.simulator.domain.SimulatedMeter;
import com.enerlytics.simulator.domain.SimulationProfile;
import com.enerlytics.simulator.domain.TelemetryEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryGeneratorTest {

    private final LoadProfileCalculator calculator = new LoadProfileCalculator();
    private final TelemetryGenerator generator = new TelemetryGenerator(calculator);

    @Test
    void eventContainsAllRequiredFields() {
        SimulatedMeter meter = new SimulatedMeter(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 60, SimulationProfile.OFFICE, "UTC");
        Instant timestamp = Instant.parse("2026-01-05T12:00:00Z");

        TelemetryEvent event = generator.generate(meter, timestamp, 0, 12345L, 0.0);

        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getMeterId()).isEqualTo(meter.meterId());
        assertThat(event.getOrganizationId()).isEqualTo(meter.organizationId());
        assertThat(event.getSiteId()).isEqualTo(meter.siteId());
        assertThat(event.getTimestamp()).isEqualTo(timestamp);
        assertThat(event.getPowerKw()).isNotNull();
        assertThat(event.getEnergyKwh()).isNotNull();
        assertThat(event.getVoltage()).isNotNull();
        assertThat(event.getCurrent()).isNotNull();
        assertThat(event.getPowerFactor()).isNotNull();
        assertThat(event.getFrequency()).isNotNull();
        assertThat(event.getQualityStatus()).isEqualTo("OK");
    }

    @Test
    void deterministicSeedProducesSameReadings() {
        SimulatedMeter meter = new SimulatedMeter(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 60, SimulationProfile.WAREHOUSE, "UTC");
        Instant timestamp = Instant.parse("2026-01-05T12:00:00Z");

        TelemetryGenerator firstGenerator = new TelemetryGenerator(calculator);
        TelemetryGenerator secondGenerator = new TelemetryGenerator(calculator);

        TelemetryEvent first = firstGenerator.generate(meter, timestamp, 0, 999L, 0.0);
        TelemetryEvent second = secondGenerator.generate(meter, timestamp, 0, 999L, 0.0);

        assertThat(first.getPowerKw()).isEqualByComparingTo(second.getPowerKw());
        assertThat(first.getEventId()).isEqualTo(second.getEventId());
    }

    @Test
    void eventIdsAreUniqueAcrossMetersAndTimestamps() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            SimulatedMeter meter = new SimulatedMeter(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 60, SimulationProfile.RETAIL, "UTC");
            Instant timestamp = Instant.parse("2026-01-05T12:00:00Z").plusSeconds(i * 60L);
            TelemetryEvent event = generator.generate(meter, timestamp, i, null, 0.0);
            assertThat(ids).doesNotContain(event.getEventId());
            ids.add(event.getEventId());
        }
    }

    @Test
    void energyMatchesPowerAndInterval() {
        SimulatedMeter meter = new SimulatedMeter(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 60, SimulationProfile.DATA_CENTER, "UTC");
        Instant timestamp = Instant.parse("2026-01-05T12:00:00Z");

        TelemetryEvent event = generator.generate(meter, timestamp, 0, 111L, 0.0);
        double expected = event.getPowerKw().doubleValue() * (60.0 / 3600.0);
        assertThat(event.getEnergyKwh().doubleValue()).isEqualTo(expected, org.assertj.core.data.Offset.offset(1e-9));
    }
}
