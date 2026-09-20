package com.enerlytics.simulator.infrastructure;

import com.enerlytics.simulator.config.SimulatorProperties;
import com.enerlytics.simulator.domain.SimulatedMeter;
import com.enerlytics.simulator.domain.SimulationProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "enerlytics.simulator.use-registry", havingValue = "true")
public class RegistryMeterSource implements MeterSource {

    private static final Logger log = LoggerFactory.getLogger(RegistryMeterSource.class);
    private static final TypeReference<List<RegistryMeterDto>> LIST_TYPE = new TypeReference<>() {};

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SimulatorProperties properties;

    public RegistryMeterSource(ObjectMapper objectMapper, SimulatorProperties properties) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<SimulatedMeter> fetchActiveSimulatedMeters() {
        if (!properties.isUseRegistry()) {
            throw new IllegalStateException("Registry source is disabled");
        }
        String url = properties.getRegistryUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Simulator registry URL is not configured");
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET();
            if (properties.getRegistryApiKey() != null && !properties.getRegistryApiKey().isBlank()) {
                requestBuilder.header("X-Internal-Api-Key", properties.getRegistryApiKey());
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Registry returned status {} for simulated meters", response.statusCode());
                return Collections.emptyList();
            }

            List<RegistryMeterDto> meters = objectMapper.readValue(response.body(), LIST_TYPE);
            return meters.stream().map(this::toDomain).toList();
        } catch (Exception e) {
            log.warn("Failed to fetch simulated meters from registry: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private SimulatedMeter toDomain(RegistryMeterDto dto) {
        return new SimulatedMeter(
                UUID.fromString(dto.meterId()),
                UUID.fromString(dto.organizationId()),
                UUID.fromString(dto.siteId()),
                dto.readingIntervalSeconds(),
                dto.simulationProfile() != null ? SimulationProfile.valueOf(dto.simulationProfile()) : SimulationProfile.OFFICE,
                dto.timezone() != null ? dto.timezone() : "UTC"
        );
    }

    public record RegistryMeterDto(
            String meterId,
            String organizationId,
            String siteId,
            String buildingId,
            String zoneId,
            int readingIntervalSeconds,
            String simulationProfile,
            String timezone) {
    }
}
