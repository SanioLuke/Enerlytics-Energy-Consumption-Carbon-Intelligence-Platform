package com.enerlytics.organization.api;

import com.enerlytics.organization.api.dto.OrganizationResponse;
import com.enerlytics.organization.application.OrganizationService;
import com.enerlytics.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Organization management")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organization:read')")
    @Operation(summary = "Get an organization by id")
    public ResponseEntity<OrganizationResponse> getOrganization(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (!principal.isPlatformAdmin() && !id.equals(principal.getCurrentOrganizationId())) {
            throw new AccessDeniedException("Cross-tenant access denied");
        }
        return ResponseEntity.ok(organizationService.getOrganization(id));
    }
}
