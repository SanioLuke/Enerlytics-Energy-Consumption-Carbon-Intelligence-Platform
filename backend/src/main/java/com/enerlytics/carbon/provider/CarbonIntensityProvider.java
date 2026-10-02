package com.enerlytics.carbon.provider;

import java.time.Instant;
import java.util.List;

/**
 * Abstraction for retrieving grid carbon intensity observations from an external
 * provider. Implementations are responsible for transport, caching, and resilience;
 * callers persist observations through the domain service.
 */
public interface CarbonIntensityProvider {

    String name();

    CarbonIntensity latest(String zone);

    List<CarbonIntensity> history(String zone, Instant from, Instant to);

    /**
     * Forecasts may not be supported by every provider or plan.
     *
     * @return forecasted observations; empty list if unsupported.
     */
    List<CarbonIntensity> forecast(String zone);

    ProviderHealth health();
}
