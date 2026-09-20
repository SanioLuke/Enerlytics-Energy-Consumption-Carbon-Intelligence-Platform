package com.enerlytics.simulator.infrastructure;

import com.enerlytics.simulator.domain.SimulatedMeter;

import java.util.List;

public interface MeterSource {
    List<SimulatedMeter> fetchActiveSimulatedMeters();
}
