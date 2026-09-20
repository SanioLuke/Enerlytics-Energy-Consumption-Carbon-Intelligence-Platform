package com.enerlytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Bootstrap security configuration.
 * <p>
 * This is a technical placeholder that permits all requests so that actuator health checks
 * and local development endpoints work without authentication. Real authorization will be
 * implemented when identity and RBAC features are introduced.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/error").permitAll()
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
