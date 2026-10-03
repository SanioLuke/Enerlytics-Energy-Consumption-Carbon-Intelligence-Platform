package com.enerlytics.anomaly.api;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.anomaly.api.dto.AnomalyResponse;
import com.enerlytics.anomaly.api.dto.MaintenanceWindowRequest;
import com.enerlytics.anomaly.api.dto.MaintenanceWindowResponse;
import com.enerlytics.anomaly.application.AnomalyDetectionService;
import com.enerlytics.anomaly.application.MaintenanceWindowService;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/anomalies")
@Tag(name = "Energy Anomalies", description = "Interpretable energy anomaly detection and history")
@SecurityRequirement(name = "bearerAuth")
public class AnomalyController {

    private final AnomalyDetectionService detectionService;
    private final MaintenanceWindowService maintenanceService;

    public AnomalyController(AnomalyDetectionService detectionService,
                             MaintenanceWindowService maintenanceService) {
        this.detectionService = detectionService;
        this.maintenanceService = maintenanceService;
    }

    @PostMapping("/detect")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<List<AnomalyResponse>> detect(
            @PathVariable UUID orgId,
            @RequestParam DimensionType entityType,
            @RequestParam UUID entityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(detectionService.detect(orgId, entityType, entityId, from, to));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('analytics:read')")
    public ResponseEntity<List<AnomalyResponse>> history(
            @PathVariable UUID orgId,
            @RequestParam(required = false) DimensionType entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        if ((entityType == null) != (entityId == null)) {
            throw new IllegalArgumentException("entityType and entityId must be provided together");
        }
        List<AnomalyResponse> result = entityType == null
                ? detectionService.organizationHistory(orgId, from, to)
                : detectionService.history(orgId, entityType, entityId, from, to);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/maintenance-windows")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<MaintenanceWindowResponse> createMaintenanceWindow(
            @PathVariable UUID orgId,
            @Valid @RequestBody MaintenanceWindowRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(maintenanceService.create(orgId, request, principal.getEmail()));
    }

    @GetMapping("/maintenance-windows")
    @PreAuthorize("hasAuthority('alert:read')")
    public ResponseEntity<List<MaintenanceWindowResponse>> maintenanceWindows(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(maintenanceService.list(orgId));
    }

    @DeleteMapping("/maintenance-windows/{windowId}")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<Void> deactivateMaintenanceWindow(
            @PathVariable UUID orgId,
            @PathVariable UUID windowId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        maintenanceService.deactivate(orgId, windowId);
        return ResponseEntity.noContent().build();
    }
}
