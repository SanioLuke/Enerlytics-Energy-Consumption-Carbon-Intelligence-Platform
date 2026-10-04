package com.enerlytics.live.energy.api;

import com.enerlytics.live.energy.application.LiveEnergyService;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/live/energy")
@Tag(name = "Live Energy", description = "Real-time demand and meter telemetry snapshots")
@SecurityRequirement(name = "bearerAuth")
public class LiveEnergyController {

    private final LiveEnergyService liveEnergyService;

    public LiveEnergyController(LiveEnergyService liveEnergyService) {
        this.liveEnergyService = liveEnergyService;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('telemetry:read')")
    @Operation(summary = "Subscribe to real-time energy snapshots via SSE")
    public SseEmitter subscribe(
            @PathVariable UUID orgId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID buildingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return liveEnergyService.subscribe(orgId, siteId, buildingId);
    }

    @GetMapping("/snapshot")
    @PreAuthorize("hasAuthority('telemetry:read')")
    @Operation(summary = "Get a single current snapshot (fallback when SSE is unavailable)")
    public ResponseEntity<LiveEnergySnapshot> snapshot(
            @PathVariable UUID orgId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID buildingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(liveEnergyService.snapshot(orgId, siteId, buildingId));
    }
}
