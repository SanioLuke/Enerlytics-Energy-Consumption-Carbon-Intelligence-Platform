package com.enerlytics.carbon.api.dto;

import com.enerlytics.analytics.domain.DimensionType;

import java.math.BigDecimal;
import java.util.UUID;

public record FacilityEmissionResponse(
        DimensionType dimension,
        UUID dimensionId,
        String name,
        String gridRegionCode,
        BigDecimal energyConsumedKwh,
        BigDecimal emissionsKgCo2Eq) {
}
