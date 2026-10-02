package com.enerlytics.carbon.provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic mock provider for local development when no external API key is
 * configured. All returned values are explicitly simulated and should never
 * be mistaken for live grid data.
 */
public class MockCarbonIntensityProvider implements CarbonIntensityProvider {

    public static final String PROVIDER_NAME = "MOCK";
    private static final int BASE_INTENSITY = 350;
    private static final int MAX_VARIANCE = 150;

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public CarbonIntensity latest(String zone) {
        Instant now = Instant.now();
        return new CarbonIntensity(
                PROVIDER_NAME, zone, now.truncatedTo(java.time.temporal.ChronoUnit.HOURS),
                deterministicValue(zone, now), true, Instant.now());
    }

    @Override
    public List<CarbonIntensity> history(String zone, Instant from, Instant to) {
        List<CarbonIntensity> results = new ArrayList<>();
        Instant cursor = from.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        while (cursor.isBefore(to)) {
            results.add(new CarbonIntensity(
                    PROVIDER_NAME, zone, cursor, deterministicValue(zone, cursor), true, Instant.now()));
            cursor = cursor.plusSeconds(3600);
        }
        return results;
    }

    @Override
    public List<CarbonIntensity> forecast(String zone) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        List<CarbonIntensity> results = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            Instant ts = now.plusSeconds(i * 3600L);
            results.add(new CarbonIntensity(PROVIDER_NAME, zone, ts, deterministicValue(zone, ts), true, Instant.now()));
        }
        return results;
    }

    @Override
    public ProviderHealth health() {
        return new ProviderHealth(ProviderStatus.UP, Instant.now(), "Mock provider is healthy and returning simulated data");
    }

    private int deterministicValue(String zone, Instant timestamp) {
        int zoneHash = Math.abs(zone.hashCode());
        long hour = timestamp.getEpochSecond() / 3600;
        int variance = (int) ((zoneHash + hour) % (2L * MAX_VARIANCE));
        return BASE_INTENSITY + variance - MAX_VARIANCE;
    }
}
