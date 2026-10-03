package com.enerlytics.anomaly.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AnomalyDetectionProperties.class)
public class AnomalyConfig {
}
