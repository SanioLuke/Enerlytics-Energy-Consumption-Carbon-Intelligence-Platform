package com.enerlytics.carbon.api;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.carbon.api.dto.*;
import com.enerlytics.carbon.application.CarbonAnalyticsService;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/analytics/carbon")
@Tag(name = "Carbon Analytics", description = "Carbon intensity and emissions analytics")
@SecurityRequirement(name = "bearerAuth")
public class CarbonAnalyticsController {

    private final CarbonAnalyticsService carbonAnalyticsService;

    public CarbonAnalyticsController(CarbonAnalyticsService carbonAnalyticsService) {
        this.carbonAnalyticsService = carbonAnalyticsService;
    }

    @GetMapping("/intensity/current")
    @PreAuthorize("hasAuthority('carbon:read')")
    public ResponseEntity<CarbonIntensityResponse> currentIntensity(
            @PathVariable UUID orgId,
            @RequestParam UUID siteId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(carbonAnalyticsService.currentIntensity(orgId, siteId));
    }

    @GetMapping("/emissions/today")
    @PreAuthorize("hasAuthority('carbon:read')")
    public ResponseEntity<List<CarbonEmissionResponse>> todayEmissions(
            @PathVariable UUID orgId,
            @RequestParam @NotNull DimensionType dimension,
            @RequestParam @NotNull UUID dimensionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(carbonAnalyticsService.todayEmissions(orgId, dimension, dimensionId));
    }

    @GetMapping("/emissions")
    @PreAuthorize("hasAuthority('carbon:read')")
    public ResponseEntity<List<CarbonEmissionResponse>> emissions(
            @PathVariable UUID orgId,
            @RequestParam @NotNull DimensionType dimension,
            @RequestParam @NotNull UUID dimensionId,
            @RequestParam @NotNull AggregationGranularity granularity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(from, to);
        return ResponseEntity.ok(carbonAnalyticsService.emissions(orgId, dimension, dimensionId, granularity, from, to));
    }

    @GetMapping("/site-comparison")
    @PreAuthorize("hasAuthority('carbon:read')")
    public ResponseEntity<List<SiteComparisonResponse>> siteComparison(
            @PathVariable UUID orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(from, to);
        return ResponseEntity.ok(carbonAnalyticsService.siteComparison(orgId, from, to));
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('carbon:read')")
    public ResponseEntity<List<CarbonEmissionResponse>> trend(
            @PathVariable UUID orgId,
            @RequestParam @NotNull DimensionType dimension,
            @RequestParam @NotNull UUID dimensionId,
            @RequestParam @NotNull AggregationGranularity granularity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(from, to);
        return ResponseEntity.ok(carbonAnalyticsService.trend(orgId, dimension, dimensionId, granularity, from, to));
    }

    @GetMapping("/intensity")
    @PreAuthorize("hasAuthority('carbon:read')")
    public ResponseEntity<List<CarbonEmissionResponse>> realizedIntensity(
            @PathVariable UUID orgId,
            @RequestParam @NotNull DimensionType dimension,
            @RequestParam @NotNull UUID dimensionId,
            @RequestParam @NotNull AggregationGranularity granularity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(from, to);
        return ResponseEntity.ok(carbonAnalyticsService.realizedIntensity(orgId, dimension, dimensionId, granularity, from, to));
    }

    @GetMapping("/per-floor-area")
    @PreAuthorize("hasAuthority('carbon:read')")
    public ResponseEntity<CarbonPerFloorAreaResponse> perFloorArea(
            @PathVariable UUID orgId,
            @RequestParam UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(from, to);
        return ResponseEntity.ok(carbonAnalyticsService.perFloorArea(orgId, siteId, from, to));
    }

    @GetMapping("/by-facility")
    @PreAuthorize("hasAuthority('carbon:read')")
    public ResponseEntity<List<FacilityEmissionResponse>> byFacility(
            @PathVariable UUID orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(from, to);
        return ResponseEntity.ok(carbonAnalyticsService.byFacility(orgId, from, to));
    }

    @GetMapping("/provider-quality")
    @PreAuthorize("hasAuthority('carbon:read')")
    public ResponseEntity<List<ProviderQualityResponse>> providerQuality(
            @PathVariable UUID orgId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(from, to);
        return ResponseEntity.ok(carbonAnalyticsService.providerQuality(orgId, from, to));
    }

    private void validateRange(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
        if (java.time.Duration.between(from, to).toDays() > 366) {
            throw new IllegalArgumentException("Requested range must not exceed 366 days");
        }
    }
}
