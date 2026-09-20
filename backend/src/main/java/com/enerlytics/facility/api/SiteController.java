package com.enerlytics.facility.api;

import com.enerlytics.common.api.PageResponse;
import com.enerlytics.facility.api.dto.CreateSiteRequest;
import com.enerlytics.facility.api.dto.SiteResponse;
import com.enerlytics.facility.api.dto.UpdateSiteRequest;
import com.enerlytics.facility.application.SiteService;
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
@RequestMapping("/api/v1/organizations/{orgId}/sites")
@Tag(name = "Sites", description = "Site management")
@SecurityRequirement(name = "bearerAuth")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('site:write')")
    @Operation(summary = "Create a site")
    public ResponseEntity<SiteResponse> create(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateSiteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        SiteResponse response = siteService.create(orgId, request);
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + orgId + "/sites/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('site:read')")
    @Operation(summary = "List sites")
    public ResponseEntity<PageResponse<SiteResponse>> list(
            @PathVariable UUID orgId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "name,asc") String[] sort,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(siteService.list(orgId, search, page, size, sort));
    }

    @GetMapping("/{siteId}")
    @PreAuthorize("hasAuthority('site:read')")
    @Operation(summary = "Get a site by id")
    public ResponseEntity<SiteResponse> get(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(siteService.get(orgId, siteId));
    }

    @PutMapping("/{siteId}")
    @PreAuthorize("hasAuthority('site:write')")
    @Operation(summary = "Update a site")
    public ResponseEntity<SiteResponse> update(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @Valid @RequestBody UpdateSiteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(siteService.update(orgId, siteId, request));
    }

    @DeleteMapping("/{siteId}")
    @PreAuthorize("hasAuthority('site:write')")
    @Operation(summary = "Archive a site")
    public ResponseEntity<Void> delete(
            @PathVariable UUID orgId,
            @PathVariable UUID siteId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        siteService.delete(orgId, siteId);
        return ResponseEntity.noContent().build();
    }
}
