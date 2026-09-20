package com.enerlytics.meter.api.internal;

import com.enerlytics.meter.api.dto.SimulatedMeterResponse;
import com.enerlytics.meter.application.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/simulation")
public class SimulationInternalController {

    private final SimulationService simulationService;

    public SimulationInternalController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/meters")
    public ResponseEntity<List<SimulatedMeterResponse>> activeSimulatedMeters() {
        return ResponseEntity.ok(simulationService.findActiveSimulatedMeters());
    }
}
