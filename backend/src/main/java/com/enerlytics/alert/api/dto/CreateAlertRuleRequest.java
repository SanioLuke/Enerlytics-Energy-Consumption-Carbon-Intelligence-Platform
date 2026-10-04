package com.enerlytics.alert.api.dto;

import com.enerlytics.analytics.domain.DimensionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAlertRuleRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "HIGH_CONSUMPTION|HIGH_DEMAND|CARBON_INTENSITY_HIGH|METER_OFFLINE|ABNORMAL_USAGE|TARGET_EXCEEDED") String alertType,
        @NotNull DimensionType scopeType,
        @NotNull UUID scopeId,
        @NotBlank @Size(max = 64) String metric,
        @NotBlank @Pattern(regexp = "GT|GTE|LT|LTE|EQ|NE") String comparisonOperator,
        @NotNull @Digits(integer = 18, fraction = 9) BigDecimal threshold,
        @NotNull @Min(60) @Max(2592000) Long evaluationWindowSeconds,
        @NotBlank @Pattern(regexp = "INFO|WARNING|CRITICAL") String severity,
        @NotNull @Min(0) @Max(2592000) Long cooldownSeconds,
        boolean enabled) {
}
