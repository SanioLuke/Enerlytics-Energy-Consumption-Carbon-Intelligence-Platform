package com.enerlytics.forecast.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ForecastProperties.class)
public class ForecastConfig {
}
