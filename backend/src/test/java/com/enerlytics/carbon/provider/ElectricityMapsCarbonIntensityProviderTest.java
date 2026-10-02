package com.enerlytics.carbon.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElectricityMapsCarbonIntensityProviderTest {

    private MockWebServer server;
    private ElectricityMapsCarbonIntensityProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        CarbonIntensityProperties properties = new CarbonIntensityProperties();
        properties.getElectricityMaps().setApiKey("test-api-key");
        properties.getElectricityMaps().setBaseUrl(server.url("/").toString());
        properties.getElectricityMaps().setConnectTimeout(Duration.ofSeconds(2));
        properties.getElectricityMaps().setReadTimeout(Duration.ofSeconds(2));
        properties.getElectricityMaps().setMinRequestInterval(Duration.ZERO);
        properties.getElectricityMaps().setForecastEnabled(true);
        provider = new ElectricityMapsCarbonIntensityProvider(
                RestClient.builder(), properties, meterRegistry, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void latestParsesValidResponse() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"zone":"DE","carbonIntensity":318,"datetime":"2026-01-15T10:00:00.000Z","updatedAt":"2026-01-15T10:00:00.000Z","createdAt":"2026-01-15T10:00:00.000Z","emissionFactorType":"lifecycle","isEstimated":false,"estimationMethod":"FORECASTS_HIERARCHY"}
                        """));

        CarbonIntensity result = provider.latest("DE");

        assertThat(result.provider()).isEqualTo(ElectricityMapsCarbonIntensityProvider.PROVIDER_NAME);
        assertThat(result.zone()).isEqualTo("DE");
        assertThat(result.carbonIntensityGCo2EqPerKwh()).isEqualTo(318);
        assertThat(result.estimated()).isFalse();
        assertThat(result.timestamp()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("/carbon-intensity/latest?zone=DE");
        assertThat(request.getHeader("auth-token")).isEqualTo("test-api-key");
    }

    @Test
    void missingApiKeyThrowsAuthenticationError() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"unauthorized\"}"));

        assertThatThrownBy(() -> provider.latest("DE"))
                .isInstanceOf(ProviderAuthenticationException.class);
    }

    @Test
    void rateLimit429IsTranslated() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":\"rate limit\"}"));

        assertThatThrownBy(() -> provider.latest("DE"))
                .isInstanceOf(ProviderRateLimitException.class);
    }

    @Test
    void serverErrorIsTranslatedToRetryableException() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));

        assertThatThrownBy(() -> provider.latest("DE"))
                .isInstanceOf(ProviderServerException.class);
    }

    @Test
    void invalidJsonThrowsResponseException() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not-json"));

        assertThatThrownBy(() -> provider.latest("DE"))
                .isInstanceOf(ProviderResponseException.class);
    }

    @Test
    void missingCarbonIntensityThrowsResponseException() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("""
                        {"zone":"DE","datetime":"2026-01-15T10:00:00.000Z"}
                        """));

        assertThatThrownBy(() -> provider.latest("DE"))
                .isInstanceOf(ProviderResponseException.class);
    }

    @Test
    void historyFiltersByTimeRange() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"zone":"DE","history":[
                            {"zone":"DE","carbonIntensity":100,"datetime":"2026-01-15T08:00:00.000Z","isEstimated":false},
                            {"zone":"DE","carbonIntensity":200,"datetime":"2026-01-15T09:00:00.000Z","isEstimated":true},
                            {"zone":"DE","carbonIntensity":300,"datetime":"2026-01-15T10:00:00.000Z","isEstimated":false}
                        ]}
                        """));

        Instant from = Instant.parse("2026-01-15T08:30:00Z");
        Instant to = Instant.parse("2026-01-15T10:00:00Z");
        List<CarbonIntensity> history = provider.history("DE", from, to);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).carbonIntensityGCo2EqPerKwh()).isEqualTo(200);
        assertThat(history.get(0).estimated()).isTrue();
    }

    @Test
    void forecastReturnsParsedEntries() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"zone":"DE","forecast":[
                            {"carbonIntensity":250,"datetime":"2026-01-16T00:00:00.000Z","isEstimated":false}
                        ]}
                        """));

        List<CarbonIntensity> forecast = provider.forecast("DE");

        assertThat(forecast).hasSize(1);
        assertThat(forecast.get(0).carbonIntensityGCo2EqPerKwh()).isEqualTo(250);
    }

    @Test
    void rateLimitAwarenessEnforcesMinimumInterval() {
        CarbonIntensityProperties properties = new CarbonIntensityProperties();
        properties.getElectricityMaps().setApiKey("test-api-key");
        properties.getElectricityMaps().setBaseUrl(server.url("/").toString());
        properties.getElectricityMaps().setConnectTimeout(Duration.ofSeconds(2));
        properties.getElectricityMaps().setReadTimeout(Duration.ofSeconds(2));
        properties.getElectricityMaps().setMinRequestInterval(Duration.ofDays(1));
        properties.getElectricityMaps().setForecastEnabled(false);
        ElectricityMapsCarbonIntensityProvider throttled = new ElectricityMapsCarbonIntensityProvider(
                RestClient.builder(), properties, new SimpleMeterRegistry(), objectMapper);
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"zone":"DE","carbonIntensity":318,"datetime":"2026-01-15T10:00:00.000Z"}
                        """));

        throttled.latest("DE");

        assertThatThrownBy(() -> throttled.latest("DE"))
                .isInstanceOf(ProviderRateLimitException.class)
                .hasMessageContaining("throttled");
    }
}
