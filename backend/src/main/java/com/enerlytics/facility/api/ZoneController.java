package com.enerlytics.facility.api;

import com.enerlytics.common.api.PageResponse;
import com.enerlytics.facility.api.dto.CreateZoneRequest;
import com.enerlytics.facility.api.dto.UpdateZoneRequest;
import com.enerlytics.facility.api.dto.ZoneResponse;
import com.enerlytics.facility.application.ZoneService;
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
@RequestMapping("/api/v1/organizations/{orgId}/sites/{siteId}/buildings/{buildingId}/zones")
@Tag(name = "Zones", description = "Zone management")
@SecurityRequirement(name = "bearerAuth")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('site:write')")
    @Operation(summary = "Create a zone")
    public ResponseEntity<ZoneResponse> create(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @PathVariable UUID buildingId,
            @Valid @RequestBody CreateZoneRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        ZoneResponse response = zoneService.create(orgId, siteId, request);
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + orgId + "/sites/" + siteId + "/buildings/" + buildingId + "/zones/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('site:read')")
    @Operation(summary = "List zones")
    public ResponseEntity<PageResponse<ZoneResponse>> list(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @PathVariable UUID buildingId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "name,asc") String[] sort,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(zoneService.list(orgId, siteId, buildingId, search, page, size, sort));
    }

    @GetMapping("/{zoneId}")
    @PreAuthorize("hasAuthority('site:read')")
    @Operation(summary = "Get a zone by id")
    public ResponseEntity<ZoneResponse> get(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @PathVariable UUID buildingId,
            @PathVariable UUID zoneId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(zoneService.get(orgId, siteId, zoneId));
    }

    @PutMapping("/{zoneId}")
    @PreAuthorize("hasAuthority('site:write')")
    @Operation(summary = "Update a zone")
    public ResponseEntity<ZoneResponse> update(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @PathVariable UUID buildingId,
            @PathVariable UUID zoneId,
            @Valid @RequestBody UpdateZoneRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(zoneService.update(orgId, siteId, zoneId, request));
    }

    @DeleteMapping("/{zoneId}")
    @PreAuthorize("hasAuthority('site:write')")
    @Operation(summary = "Archive a zone")
    public ResponseEntity<Void> delete(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @PathVariable UUID buildingId,
            @PathVariable UUID zoneId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        zoneService.delete(orgId, siteId, zoneId);
        return ResponseEntity.noContent().build();
    }
}
