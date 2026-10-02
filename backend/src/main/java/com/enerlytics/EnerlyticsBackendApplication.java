package com.enerlytics;

import com.enerlytics.config.DevAdminProperties;
import com.enerlytics.security.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableConfigurationProperties({JwtProperties.class, DevAdminProperties.class})
public class EnerlyticsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnerlyticsBackendApplication.class, args);
    }
}
