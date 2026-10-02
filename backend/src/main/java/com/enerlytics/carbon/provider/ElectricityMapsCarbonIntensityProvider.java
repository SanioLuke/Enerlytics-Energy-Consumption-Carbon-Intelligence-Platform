package com.enerlytics.carbon.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade client for the Electricity Maps carbon intensity API.
 *
 * <p>API reference: https://api.electricitymap.org/v3/carbon-intensity/{latest|history|forecast}</p>
 */
public class ElectricityMapsCarbonIntensityProvider implements CarbonIntensityProvider {

    public static final String PROVIDER_NAME = "ELECTRICITY_MAPS";
    private static final Logger log = LoggerFactory.getLogger(ElectricityMapsCarbonIntensityProvider.class);

    private final RestClient restClient;
    private final CarbonIntensityProperties.ElectricityMaps config;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, Instant> lastRequestByZone = new ConcurrentHashMap<>();

    public ElectricityMapsCarbonIntensityProvider(RestClient.Builder restClientBuilder,
                                                  CarbonIntensityProperties properties,
                                                  MeterRegistry meterRegistry,
                                                  ObjectMapper objectMapper) {
        this.config = properties.getElectricityMaps();
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(config.getBaseUrl())
                .requestFactory(timeoutFactory())
                .defaultHeader("auth-token", config.getApiKey())
                .defaultUriVariables(Map.of())
                .build();
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    @Cacheable(value = "carbonIntensityLatest", key = "#zone")
    @Retry(name = "electricitymaps")
    @CircuitBreaker(name = "electricitymaps")
    public CarbonIntensity latest(String zone) {
        enforceRateLimit(zone);
        Instant retrievedAt = Instant.now();
        JsonNode body = timedRequest(zone, "/carbon-intensity/latest", Map.of("zone", zone));
        CarbonIntensity result = parseSingle(PROVIDER_NAME, zone, body, retrievedAt);
        recordRequest(zone, true, null);
        return result;
    }

    @Override
    @Retry(name = "electricitymaps")
    @CircuitBreaker(name = "electricitymaps")
    public List<CarbonIntensity> history(String zone, Instant from, Instant to) {
        enforceRateLimit(zone);
        Instant retrievedAt = Instant.now();
        JsonNode body = timedRequest(zone, "/carbon-intensity/history", Map.of("zone", zone));
        List<CarbonIntensity> results = parseHistory(PROVIDER_NAME, zone, body, retrievedAt, from, to);
        recordRequest(zone, true, null);
        return results;
    }

    @Override
    @Retry(name = "electricitymaps")
    @CircuitBreaker(name = "electricitymaps")
    public List<CarbonIntensity> forecast(String zone) {
        if (!config.isForecastEnabled()) {
            return List.of();
        }
        enforceRateLimit(zone);
        Instant retrievedAt = Instant.now();
        JsonNode body = timedRequest(zone, "/carbon-intensity/forecast", Map.of("zone", zone));
        List<CarbonIntensity> results = parseForecast(PROVIDER_NAME, zone, body, retrievedAt);
        recordRequest(zone, true, null);
        return results;
    }

    @Override
    public ProviderHealth health() {
        try {
            latest("DE");
            return new ProviderHealth(ProviderStatus.UP, Instant.now(), "Latest request succeeded");
        } catch (ProviderAuthenticationException e) {
            return new ProviderHealth(ProviderStatus.DOWN, Instant.now(), "Authentication failure: " + e.getMessage());
        } catch (ProviderRateLimitException e) {
            return new ProviderHealth(ProviderStatus.DEGRADED, Instant.now(), "Rate limited: " + e.getMessage());
        } catch (ProviderException e) {
            return new ProviderHealth(ProviderStatus.DEGRADED, Instant.now(), "Provider error: " + e.getMessage());
        } catch (Exception e) {
            return new ProviderHealth(ProviderStatus.DOWN, Instant.now(), "Unexpected error: " + e.getMessage());
        }
    }

    private JsonNode timedRequest(String zone, String path, Map<String, Object> params) {
        Timer.Sample sample = Timer.start(meterRegistry);
        URI uri = URI.create(config.getBaseUrl() + path);
        try {
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path(path).queryParam("zone", zone);
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(status -> status.isSameCodeAs(org.springframework.http.HttpStatus.UNAUTHORIZED),
                            (request, response) -> {
                                throw new ProviderAuthenticationException("Invalid or missing Electricity Maps API key");
                            })
                    .onStatus(org.springframework.http.HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                if (response.getStatusCode().value() == 429) {
                                    throw new ProviderRateLimitException("Electricity Maps rate limit exceeded (429)");
                                }
                                throw new ProviderClientException("Electricity Maps client error " + response.getStatusCode());
                            })
                    .onStatus(org.springframework.http.HttpStatusCode::is5xxServerError,
                            (request, response) -> {
                                throw new ProviderServerException("Electricity Maps server error " + response.getStatusCode());
                            })
                    .body(JsonNode.class);
            if (body == null) {
                throw new ProviderResponseException("Empty response from Electricity Maps");
            }
            sample.stop(meterRegistry.timer("carbon.intensity.provider.latency", "provider", PROVIDER_NAME, "zone", zone));
            return body;
        } catch (ResourceAccessException e) {
            sample.stop(meterRegistry.timer("carbon.intensity.provider.latency", "provider", PROVIDER_NAME, "zone", zone));
            throw new ProviderServerException("Electricity Maps request failed: " + e.getMessage(), e);
        } catch (ProviderException e) {
            sample.stop(meterRegistry.timer("carbon.intensity.provider.latency", "provider", PROVIDER_NAME, "zone", zone));
            throw e;
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("carbon.intensity.provider.latency", "provider", PROVIDER_NAME, "zone", zone));
            throw new ProviderResponseException("Unexpected Electricity Maps response: " + e.getMessage(), e);
        }
    }

    private void enforceRateLimit(String zone) {
        Instant now = Instant.now();
        Instant last = lastRequestByZone.putIfAbsent(zone, now);
        if (last != null) {
            Duration elapsed = Duration.between(last, now);
            if (elapsed.compareTo(config.getMinRequestInterval()) < 0) {
                throw new ProviderRateLimitException("Requests for zone " + zone + " throttled to one per " + config.getMinRequestInterval());
            }
            lastRequestByZone.put(zone, now);
        }
    }

    private org.springframework.http.client.ClientHttpRequestFactory timeoutFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeout());
        factory.setReadTimeout(config.getReadTimeout());
        return factory;
    }

    private CarbonIntensity parseSingle(String provider, String zone, JsonNode body, Instant retrievedAt) {
        if (!body.hasNonNull("carbonIntensity") || !body.hasNonNull("datetime")) {
            throw new ProviderResponseException("Electricity Maps response missing carbonIntensity or datetime");
        }
        int value = body.path("carbonIntensity").asInt();
        Instant timestamp = parseInstant(body.path("datetime").asText());
        boolean estimated = body.path("isEstimated").asBoolean(false);
        log.debug("Provider={} zone={} latest={} gCO2eq/kWh estimated={}", provider, zone, value, estimated);
        return new CarbonIntensity(provider, zone, timestamp, value, estimated, retrievedAt);
    }

    private List<CarbonIntensity> parseHistory(String provider, String zone, JsonNode body, Instant retrievedAt, Instant from, Instant to) {
        if (!body.hasNonNull("history")) {
            throw new ProviderResponseException("Electricity Maps history response missing history array");
        }
        List<CarbonIntensity> results = new ArrayList<>();
        for (JsonNode item : body.path("history")) {
            CarbonIntensity point = parseSingle(provider, zone, item, retrievedAt);
            if (!point.timestamp().isBefore(from) && point.timestamp().isBefore(to)) {
                results.add(point);
            }
        }
        return results;
    }

    private List<CarbonIntensity> parseForecast(String provider, String zone, JsonNode body, Instant retrievedAt) {
        if (!body.hasNonNull("forecast")) {
            throw new ProviderResponseException("Electricity Maps forecast response missing forecast array");
        }
        List<CarbonIntensity> results = new ArrayList<>();
        for (JsonNode item : body.path("forecast")) {
            if (!item.hasNonNull("carbonIntensity") || !item.hasNonNull("datetime")) {
                throw new ProviderResponseException("Electricity Maps forecast item missing carbonIntensity or datetime");
            }
            int value = item.path("carbonIntensity").asInt();
            Instant timestamp = parseInstant(item.path("datetime").asText());
            boolean estimated = item.path("isEstimated").asBoolean(false);
            results.add(new CarbonIntensity(provider, zone, timestamp, value, estimated, retrievedAt));
        }
        return results;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ProviderResponseException("Invalid datetime in Electricity Maps response: " + value);
        }
    }

    private void recordRequest(String zone, boolean success, String errorType) {
        String outcome = success ? "success" : (errorType != null ? errorType : "error");
        meterRegistry.counter("carbon.intensity.provider.requests", "provider", PROVIDER_NAME, "zone", zone, "outcome", outcome).increment();
    }
}
