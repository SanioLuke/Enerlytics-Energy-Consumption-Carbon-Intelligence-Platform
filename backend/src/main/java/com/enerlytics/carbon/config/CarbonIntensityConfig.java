package com.enerlytics.carbon.config;

import com.enerlytics.carbon.provider.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CarbonIntensityProperties.class)
public class CarbonIntensityConfig {

    @Bean
    @ConditionalOnExpression("!'${enerlytics.carbon-intensity.electricity-maps.api-key:}'.blank")
    public CarbonIntensityProvider electricityMapsCarbonIntensityProvider(
            RestClient.Builder restClientBuilder,
            CarbonIntensityProperties properties,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        return new ElectricityMapsCarbonIntensityProvider(
                restClientBuilder, properties, meterRegistry, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(CarbonIntensityProvider.class)
    public CarbonIntensityProvider mockCarbonIntensityProvider() {
        return new MockCarbonIntensityProvider();
    }
}
