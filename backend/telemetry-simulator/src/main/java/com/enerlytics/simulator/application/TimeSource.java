package com.enerlytics.simulator.application;

import java.time.Instant;

@FunctionalInterface
public interface TimeSource {
    Instant now();
}
