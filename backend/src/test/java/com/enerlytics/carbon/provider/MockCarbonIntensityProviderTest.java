package com.enerlytics.carbon.provider;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockCarbonIntensityProviderTest {

    private final MockCarbonIntensityProvider provider = new MockCarbonIntensityProvider();

    @Test
    void latestReturnsDeterministicSimulatedValue() {
        CarbonIntensity first = provider.latest("DE");
        CarbonIntensity second = provider.latest("DE");

        assertThat(first.provider()).isEqualTo(MockCarbonIntensityProvider.PROVIDER_NAME);
        assertThat(first.zone()).isEqualTo("DE");
        assertThat(first.carbonIntensityGCo2EqPerKwh()).isPositive();
        assertThat(first.estimated()).isTrue();
        assertThat(second.carbonIntensityGCo2EqPerKwh()).isEqualTo(first.carbonIntensityGCo2EqPerKwh());
    }

    @Test
    void latestDiffersByZone() {
        CarbonIntensity de = provider.latest("DE");
        CarbonIntensity fr = provider.latest("FR");

        assertThat(de.carbonIntensityGCo2EqPerKwh()).isNotEqualTo(fr.carbonIntensityGCo2EqPerKwh());
    }

    @Test
    void historyReturnsHourlyEntriesInRange() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plusSeconds(3 * 3600);

        List<CarbonIntensity> history = provider.history("DE", from, to);

        assertThat(history).hasSize(3);
        assertThat(history).allSatisfy(h -> {
            assertThat(h.provider()).isEqualTo(MockCarbonIntensityProvider.PROVIDER_NAME);
            assertThat(h.estimated()).isTrue();
        });
        assertThat(history.get(0).timestamp()).isEqualTo(from);
        assertThat(history.get(1).timestamp()).isEqualTo(from.plusSeconds(3600));
    }

    @Test
    void forecastReturnsTwentyFourSimulatedHours() {
        List<CarbonIntensity> forecast = provider.forecast("NL");

        assertThat(forecast).hasSize(24);
        assertThat(forecast).allSatisfy(f -> assertThat(f.estimated()).isTrue());
        assertThat(forecast.get(0).timestamp()).isAfter(Instant.now());
    }

    @Test
    void healthReportsUp() {
        ProviderHealth health = provider.health();

        assertThat(health.status()).isEqualTo(ProviderStatus.UP);
        assertThat(health.message()).containsIgnoringCase("simulated");
    }
}
