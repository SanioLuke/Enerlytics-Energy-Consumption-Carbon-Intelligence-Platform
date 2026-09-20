package com.enerlytics.facility.api;

import com.enerlytics.common.api.PageResponse;
import com.enerlytics.facility.api.dto.BuildingResponse;
import com.enerlytics.facility.api.dto.CreateBuildingRequest;
import com.enerlytics.facility.api.dto.UpdateBuildingRequest;
import com.enerlytics.facility.application.BuildingService;
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
@RequestMapping("/api/v1/organizations/{orgId}/sites/{siteId}/buildings")
@Tag(name = "Buildings", description = "Building management")
@SecurityRequirement(name = "bearerAuth")
public class BuildingController {

    private final BuildingService buildingService;

    public BuildingController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('site:write')")
    @Operation(summary = "Create a building")
    public ResponseEntity<BuildingResponse> create(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @Valid @RequestBody CreateBuildingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        BuildingResponse response = buildingService.create(orgId, siteId, request);
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + orgId + "/sites/" + siteId + "/buildings/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('site:read')")
    @Operation(summary = "List buildings")
    public ResponseEntity<PageResponse<BuildingResponse>> list(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "name,asc") String[] sort,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(buildingService.list(orgId, siteId, search, page, size, sort));
    }

    @GetMapping("/{buildingId}")
    @PreAuthorize("hasAuthority('site:read')")
    @Operation(summary = "Get a building by id")
    public ResponseEntity<BuildingResponse> get(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @PathVariable UUID buildingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(buildingService.get(orgId, siteId, buildingId));
    }

    @PutMapping("/{buildingId}")
    @PreAuthorize("hasAuthority('site:write')")
    @Operation(summary = "Update a building")
    public ResponseEntity<BuildingResponse> update(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @PathVariable UUID buildingId,
            @Valid @RequestBody UpdateBuildingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(buildingService.update(orgId, siteId, buildingId, request));
    }

    @DeleteMapping("/{buildingId}")
    @PreAuthorize("hasAuthority('site:write')")
    @Operation(summary = "Archive a building")
    public ResponseEntity<Void> delete(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @PathVariable UUID buildingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        buildingService.delete(orgId, siteId, buildingId);
        return ResponseEntity.noContent().build();
    }
}
