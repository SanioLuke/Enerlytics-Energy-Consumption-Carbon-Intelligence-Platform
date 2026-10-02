package com.enerlytics.billing.api;

import com.enerlytics.billing.api.dto.TariffResponse;
import com.enerlytics.billing.api.dto.UpsertTariffRequest;
import com.enerlytics.billing.application.TariffService;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/tariffs")
@Tag(name = "Tariffs", description = "Electricity tariff configuration")
@SecurityRequirement(name = "bearerAuth")
public class TariffController {

    private final TariffService tariffService;

    public TariffController(TariffService tariffService) {
        this.tariffService = tariffService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tariff:write')")
    public ResponseEntity<TariffResponse> create(
            @PathVariable UUID orgId,
            @Valid @RequestBody UpsertTariffRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(tariffService.create(orgId, request));
    }

    @GetMapping("/{tariffId}")
    @PreAuthorize("hasAuthority('tariff:read')")
    public ResponseEntity<TariffResponse> get(
            @PathVariable UUID orgId,
            @PathVariable UUID tariffId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(tariffService.get(orgId, tariffId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tariff:read')")
    public ResponseEntity<List<TariffResponse>> listForSite(
            @PathVariable UUID orgId,
            @RequestParam UUID siteId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(tariffService.listForSite(orgId, siteId));
    }

    @PutMapping("/{tariffId}")
    @PreAuthorize("hasAuthority('tariff:write')")
    public ResponseEntity<TariffResponse> update(
            @PathVariable UUID orgId,
            @PathVariable UUID tariffId,
            @Valid @RequestBody UpsertTariffRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(tariffService.update(orgId, tariffId, request));
    }

    @DeleteMapping("/{tariffId}")
    @PreAuthorize("hasAuthority('tariff:write')")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID orgId,
            @PathVariable UUID tariffId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        tariffService.deactivate(orgId, tariffId);
        return ResponseEntity.noContent().build();
    }
}
