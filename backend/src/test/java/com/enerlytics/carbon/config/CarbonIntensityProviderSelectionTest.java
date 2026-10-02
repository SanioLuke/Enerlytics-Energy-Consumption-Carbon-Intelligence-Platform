package com.enerlytics.carbon.config;

import com.enerlytics.carbon.provider.CarbonIntensityProvider;
import com.enerlytics.carbon.provider.MockCarbonIntensityProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "enerlytics.carbon-intensity.electricity-maps.api-key=",
        "spring.cache.type=simple"
})
class CarbonIntensityProviderSelectionTest {

    @Autowired(required = false)
    private CarbonIntensityProvider provider;

    @Test
    void mockProviderIsSelectedWhenApiKeyIsAbsent() {
        assertThat(provider).isNotNull();
        assertThat(provider).isInstanceOf(MockCarbonIntensityProvider.class);
        assertThat(provider.name()).isEqualTo(MockCarbonIntensityProvider.PROVIDER_NAME);
    }
}
