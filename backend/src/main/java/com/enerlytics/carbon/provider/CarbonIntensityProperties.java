package com.enerlytics.carbon.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "enerlytics.carbon-intensity")
public class CarbonIntensityProperties {

    private Duration cacheTtl = Duration.ofMinutes(5);

    private ElectricityMaps electricityMaps = new ElectricityMaps();

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public ElectricityMaps getElectricityMaps() {
        return electricityMaps;
    }

    public void setElectricityMaps(ElectricityMaps electricityMaps) {
        this.electricityMaps = electricityMaps;
    }

    public static class ElectricityMaps {
        private String apiKey;
        private String baseUrl = "https://api.electricitymap.org/v3";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(15);
        private Duration minRequestInterval = Duration.ofSeconds(1);
        private boolean forecastEnabled = true;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Duration getMinRequestInterval() {
            return minRequestInterval;
        }

        public void setMinRequestInterval(Duration minRequestInterval) {
            this.minRequestInterval = minRequestInterval;
        }

        public boolean isForecastEnabled() {
            return forecastEnabled;
        }

        public void setForecastEnabled(boolean forecastEnabled) {
            this.forecastEnabled = forecastEnabled;
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
