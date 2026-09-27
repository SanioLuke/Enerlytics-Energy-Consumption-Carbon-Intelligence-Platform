package com.enerlytics.analytics.api;

import com.enerlytics.analytics.api.dto.EnergyAggregateResponse;
import com.enerlytics.analytics.application.EnergyAnalyticsService;
import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/analytics/energy")
@Tag(name = "Energy Analytics", description = "Precomputed energy rollups by dimension and time bucket")
@SecurityRequirement(name = "bearerAuth")
public class EnergyAnalyticsController {

    private final EnergyAnalyticsService analyticsService;

    public EnergyAnalyticsController(EnergyAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('analytics:read')")
    @Operation(summary = "Energy aggregate time series for a dimension",
            description = "Returns precomputed buckets ordered by bucketStart. "
                    + "dimensionId defaults to the organization for dimension=ORGANIZATION and is required otherwise.")
    public ResponseEntity<List<EnergyAggregateResponse>> series(
            @PathVariable UUID orgId,
            @RequestParam(defaultValue = "ORGANIZATION") DimensionType dimension,
            @RequestParam(required = false) UUID dimensionId,
            @RequestParam(defaultValue = "HOUR") AggregationGranularity granularity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(analyticsService.series(orgId, dimension, dimensionId, granularity, from, to));
    }
}
