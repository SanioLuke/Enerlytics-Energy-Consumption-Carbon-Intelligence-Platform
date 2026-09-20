package com.enerlytics.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.enerlytics.simulator.config.SimulatorProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
public class TelemetrySimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetrySimulatorApplication.class, args);
    }
}
