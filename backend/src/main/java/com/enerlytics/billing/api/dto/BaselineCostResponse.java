package com.enerlytics.billing.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BaselineCostResponse(
        String currency,
        Instant currentFrom,
        Instant currentTo,
        Instant baselineFrom,
        Instant baselineTo,
        BigDecimal currentTotalCost,
        BigDecimal baselineTotalCost,
        BigDecimal deltaCost,
        BigDecimal deltaPct) {
}
