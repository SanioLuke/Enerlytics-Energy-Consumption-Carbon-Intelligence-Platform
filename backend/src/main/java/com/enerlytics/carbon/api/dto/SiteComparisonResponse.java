package com.enerlytics.carbon.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SiteComparisonResponse(
        UUID siteId,
        String siteName,
        String gridRegionCode,
        BigDecimal energyConsumedKwh,
        BigDecimal carbonIntensityGCo2EqPerKwh,
        BigDecimal emissionsKgCo2Eq) {
}
