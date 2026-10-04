package com.enerlytics.forecast.api;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.forecast.api.dto.ForecastRunResponse;
import com.enerlytics.forecast.api.dto.ForecastRunSummaryResponse;
import com.enerlytics.forecast.application.EnergyForecastService;
import com.enerlytics.forecast.domain.ForecastHorizon;
import com.enerlytics.forecast.domain.ForecastMethod;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/forecasts")
@Tag(name = "Energy Forecasts", description = "Deterministic statistical energy consumption forecasts")
@SecurityRequirement(name = "bearerAuth")
public class ForecastController {

    private final EnergyForecastService forecastService;

    public ForecastController(EnergyForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('forecast:read')")
    public ResponseEntity<ForecastRunResponse> generate(
            @PathVariable UUID orgId,
            @RequestParam DimensionType entityType,
            @RequestParam UUID entityId,
            @RequestParam ForecastHorizon horizon,
            @RequestParam(defaultValue = "TREND_ADJUSTED") ForecastMethod method,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(forecastService.generate(orgId, entityType, entityId, horizon, method));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('forecast:read')")
    public ResponseEntity<List<ForecastRunSummaryResponse>> runs(
            @PathVariable UUID orgId,
            @RequestParam DimensionType entityType,
            @RequestParam UUID entityId,
            @RequestParam(required = false) ForecastHorizon horizon,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(forecastService.runs(orgId, entityType, entityId, horizon));
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasAuthority('forecast:read')")
    public ResponseEntity<ForecastRunResponse> run(
            @PathVariable UUID orgId,
            @PathVariable UUID runId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(forecastService.run(orgId, runId));
    }

    @PostMapping("/runs/{runId}/evaluate")
    @PreAuthorize("hasAuthority('forecast:read')")
    public ResponseEntity<ForecastRunResponse> evaluate(
            @PathVariable UUID orgId,
            @PathVariable UUID runId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(forecastService.evaluate(orgId, runId));
    }
}
