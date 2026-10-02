package com.enerlytics.carbon.provider;

import java.time.Instant;

/**
 * A single provider observation.
 *
 * @param provider               provider code (e.g. ELECTRICITY_MAPS, MOCK)
 * @param zone                   provider zone identifier (e.g. DE, FR)
 * @param timestamp              observation timestamp
 * @param carbonIntensityGCo2EqPerKwh carbon intensity in gCO2eq/kWh
 * @param estimated              true when the provider flags the value as estimated
 * @param retrievedAt            local retrieval time
 */
public record CarbonIntensity(
        String provider,
        String zone,
        Instant timestamp,
        int carbonIntensityGCo2EqPerKwh,
        boolean estimated,
        Instant retrievedAt) {
}
