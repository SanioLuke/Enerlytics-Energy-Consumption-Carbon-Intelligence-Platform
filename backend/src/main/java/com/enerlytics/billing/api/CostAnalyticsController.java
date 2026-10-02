package com.enerlytics.billing.api;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.billing.api.dto.BaselineCostResponse;
import com.enerlytics.billing.api.dto.CostBucketResponse;
import com.enerlytics.billing.application.CostAnalyticsService;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/billing/costs")
@Tag(name = "Energy Costs", description = "Tariff-driven energy cost analytics")
@SecurityRequirement(name = "bearerAuth")
public class CostAnalyticsController {

    private final CostAnalyticsService costAnalyticsService;

    public CostAnalyticsController(CostAnalyticsService costAnalyticsService) {
        this.costAnalyticsService = costAnalyticsService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<List<CostBucketResponse>> costs(
            @PathVariable UUID orgId,
            @RequestParam @NotNull DimensionType dimension,
            @RequestParam @NotNull UUID dimensionId,
            @RequestParam @NotNull AggregationGranularity granularity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(from, to);
        return ResponseEntity.ok(costAnalyticsService.costs(orgId, dimension, dimensionId, granularity, from, to));
    }

    @GetMapping("/daily")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<List<CostBucketResponse>> dailyCost(
            @PathVariable UUID orgId,
            @RequestParam @NotNull DimensionType dimension,
            @RequestParam @NotNull UUID dimensionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(costAnalyticsService.dailyCost(orgId, dimension, dimensionId, date));
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<List<CostBucketResponse>> monthlyCost(
            @PathVariable UUID orgId,
            @RequestParam @NotNull DimensionType dimension,
            @RequestParam @NotNull UUID dimensionId,
            @RequestParam @NotNull String month,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (Exception e) {
            throw new IllegalArgumentException("month must be in ISO yyyy-MM format");
        }
        return ResponseEntity.ok(costAnalyticsService.monthlyCost(orgId, dimension, dimensionId, yearMonth));
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<List<CostBucketResponse>> trend(
            @PathVariable UUID orgId,
            @RequestParam @NotNull DimensionType dimension,
            @RequestParam @NotNull UUID dimensionId,
            @RequestParam @NotNull AggregationGranularity granularity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(from, to);
        return ResponseEntity.ok(costAnalyticsService.trend(orgId, dimension, dimensionId, granularity, from, to));
    }

    @GetMapping("/baseline")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<BaselineCostResponse> baselineComparison(
            @PathVariable UUID orgId,
            @RequestParam @NotNull DimensionType dimension,
            @RequestParam @NotNull UUID dimensionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant currentFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant currentTo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant baselineFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant baselineTo,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        validateRange(currentFrom, currentTo);
        validateRange(baselineFrom, baselineTo);
        return ResponseEntity.ok(costAnalyticsService.baselineComparison(
                orgId, dimension, dimensionId, currentFrom, currentTo, baselineFrom, baselineTo));
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
