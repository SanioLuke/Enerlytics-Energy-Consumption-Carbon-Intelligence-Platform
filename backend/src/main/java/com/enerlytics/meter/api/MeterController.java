package com.enerlytics.meter.api;

import com.enerlytics.common.api.PageResponse;
import com.enerlytics.meter.api.dto.*;
import com.enerlytics.meter.application.MeterService;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/meters")
@Tag(name = "Meters", description = "Electricity meter management")
@SecurityRequirement(name = "bearerAuth")
public class MeterController {

    private final MeterService meterService;

    public MeterController(MeterService meterService) {
        this.meterService = meterService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('meter:write')")
    @Operation(summary = "Register a meter")
    public ResponseEntity<MeterResponse> create(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateMeterRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        MeterResponse response = meterService.create(orgId, request);
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + orgId + "/meters/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('meter:read')")
    @Operation(summary = "List and search meters")
    public ResponseEntity<PageResponse<MeterResponse>> list(
            @PathVariable UUID orgId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) MeterStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "createdAt,desc") String[] sort,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(meterService.list(orgId, siteId, buildingId, zoneId, status, search, page, size, sort));
    }

    @GetMapping("/{meterId}")
    @PreAuthorize("hasAuthority('meter:read')")
    @Operation(summary = "Get a meter by id")
    public ResponseEntity<MeterResponse> get(
            @PathVariable UUID orgId,
            @PathVariable UUID meterId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(meterService.get(orgId, meterId));
    }

    @PutMapping("/{meterId}")
    @PreAuthorize("hasAuthority('meter:write')")
    @Operation(summary = "Update a meter")
    public ResponseEntity<MeterResponse> update(
            @PathVariable UUID orgId,
            @PathVariable UUID meterId,
            @Valid @RequestBody UpdateMeterRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(meterService.update(orgId, meterId, request));
    }

    @PostMapping("/{meterId}/activate")
    @PreAuthorize("hasAuthority('meter:write')")
    @Operation(summary = "Activate a meter")
    public ResponseEntity<MeterResponse> activate(
            @PathVariable UUID orgId,
            @PathVariable UUID meterId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(meterService.activate(orgId, meterId));
    }

    @PostMapping("/{meterId}/deactivate")
    @PreAuthorize("hasAuthority('meter:write')")
    @Operation(summary = "Deactivate a meter")
    public ResponseEntity<MeterResponse> deactivate(
            @PathVariable UUID orgId,
            @PathVariable UUID meterId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(meterService.deactivate(orgId, meterId));
    }

    @PostMapping("/{meterId}/commission")
    @PreAuthorize("hasAuthority('meter:write')")
    @Operation(summary = "Commission a meter")
    public ResponseEntity<MeterResponse> commission(
            @PathVariable UUID orgId,
            @PathVariable UUID meterId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(meterService.commission(orgId, meterId));
    }

    @PostMapping("/{meterId}/decommission")
    @PreAuthorize("hasAuthority('meter:write')")
    @Operation(summary = "Decommission a meter")
    public ResponseEntity<MeterResponse> decommission(
            @PathVariable UUID orgId,
            @PathVariable UUID meterId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(meterService.decommission(orgId, meterId));
    }

    @PostMapping("/{meterId}/location")
    @PreAuthorize("hasAuthority('meter:write')")
    @Operation(summary = "Assign meter location")
    public ResponseEntity<MeterResponse> assignLocation(
            @PathVariable UUID orgId,
            @PathVariable UUID meterId,
            @Valid @RequestBody AssignLocationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(meterService.assignLocation(orgId, meterId, request));
    }

    @PostMapping("/{meterId}/heartbeat")
    @PreAuthorize("hasAuthority('meter:write')")
    @Operation(summary = "Record a meter heartbeat")
    public ResponseEntity<MeterResponse> heartbeat(
            @PathVariable UUID orgId,
            @PathVariable UUID meterId,
            @RequestBody(required = false) HeartbeatRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(meterService.heartbeat(orgId, meterId));
    }
}
