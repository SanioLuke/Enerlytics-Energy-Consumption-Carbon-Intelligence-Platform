package com.enerlytics.carbon.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CarbonPerFloorAreaResponse(
        UUID siteId,
        String siteName,
        BigDecimal floorAreaM2,
        BigDecimal energyConsumedKwh,
        BigDecimal emissionsKgCo2Eq,
        BigDecimal kgCo2EqPerM2) {
}
