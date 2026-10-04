package com.enerlytics.live.energy.api;

import java.math.BigDecimal;
import java.time.Instant;

public record TrendPoint(Instant timestamp, BigDecimal demandKw) {
}
