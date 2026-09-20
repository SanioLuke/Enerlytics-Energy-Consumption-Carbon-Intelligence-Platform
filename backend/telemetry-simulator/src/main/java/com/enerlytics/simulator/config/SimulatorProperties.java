package com.enerlytics.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;

@ConfigurationProperties(prefix = "enerlytics.simulator")
public class SimulatorProperties {

    private boolean enabled = true;
    private Duration tickInterval = Duration.ofSeconds(1);
    private double acceleration = 1.0;
    private Long seed;
    private double anomalyProbability = 0.005;
    private Duration meterRefreshInterval = Duration.ofMinutes(1);
    private boolean useRegistry = false;
    private String registryUrl;
    private String registryApiKey;

    @NestedConfigurationProperty
    private KafkaProperties kafka = new KafkaProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTickInterval() {
        return tickInterval;
    }

    public void setTickInterval(Duration tickInterval) {
        this.tickInterval = tickInterval;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public void setAcceleration(double acceleration) {
        this.acceleration = acceleration;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public double getAnomalyProbability() {
        return anomalyProbability;
    }

    public void setAnomalyProbability(double anomalyProbability) {
        this.anomalyProbability = anomalyProbability;
    }

    public Duration getMeterRefreshInterval() {
        return meterRefreshInterval;
    }

    public void setMeterRefreshInterval(Duration meterRefreshInterval) {
        this.meterRefreshInterval = meterRefreshInterval;
    }

    public boolean isUseRegistry() {
        return useRegistry;
    }

    public void setUseRegistry(boolean useRegistry) {
        this.useRegistry = useRegistry;
    }

    public String getRegistryUrl() {
        return registryUrl;
    }

    public void setRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
    }

    public String getRegistryApiKey() {
        return registryApiKey;
    }

    public void setRegistryApiKey(String registryApiKey) {
        this.registryApiKey = registryApiKey;
    }

    public KafkaProperties getKafka() {
        return kafka;
    }

    public void setKafka(KafkaProperties kafka) {
        this.kafka = kafka;
    }

    public static class KafkaProperties {
        private String bootstrapServers = "localhost:9092";
        private String topic = "enerlytics.telemetry.meter-reading-received.v1";
        private String clientId = "telemetry-simulator";

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }
}
