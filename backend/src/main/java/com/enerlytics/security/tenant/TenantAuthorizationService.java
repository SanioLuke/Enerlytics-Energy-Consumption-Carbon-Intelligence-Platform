package com.enerlytics.security.tenant;

import com.enerlytics.identity.domain.*;
import com.enerlytics.identity.infrastructure.persistence.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class TenantAuthorizationService {

    public static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private final UserOrganizationRepository userOrganizationRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public TenantAuthorizationService(UserOrganizationRepository userOrganizationRepository,
                                        RoleAssignmentRepository roleAssignmentRepository) {
        this.userOrganizationRepository = userOrganizationRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    /**
     * Resolves the tenant-scoped authorities for a user in a given organization.
     *
     * <p>Platform administrators receive their platform role's permissions for any
     * organization. Other users must have an active membership in the requested
     * organization with at least one active role assignment.</p>
     */
    public TenantAccess resolve(UUID userId, UUID organizationId) {
        List<UserOrganizationEntity> memberships = userOrganizationRepository.findByUserId(userId);

        boolean isPlatformAdmin = memberships.stream()
                .filter(UserOrganizationEntity::isActive)
                .flatMap(m -> roleAssignmentRepository.findByUserOrganizationId(m.getId()).stream())
                .filter(a -> a.isActiveAt(Instant.now()))
                .anyMatch(a -> PLATFORM_ADMIN_ROLE.equals(a.getRole().getCode()));

        Set<String> authorities = new HashSet<>();

        if (isPlatformAdmin) {
            // Add platform admin role permissions for the requested organization.
            memberships.stream()
                    .filter(UserOrganizationEntity::isActive)
                    .flatMap(m -> roleAssignmentRepository.findByUserOrganizationId(m.getId()).stream())
                    .filter(a -> a.isActiveAt(Instant.now()))
                    .filter(a -> PLATFORM_ADMIN_ROLE.equals(a.getRole().getCode()))
                    .findFirst()
                    .ifPresent(a -> addAuthorities(authorities, a.getRole()));
        }

        Optional<UserOrganizationEntity> currentMembership = memberships.stream()
                .filter(m -> m.getOrganization().getId().equals(organizationId))
                .filter(UserOrganizationEntity::isActive)
                .findFirst();

        if (currentMembership.isPresent()) {
            List<RoleAssignmentEntity> assignments = roleAssignmentRepository.findByUserOrganizationId(currentMembership.get().getId());
            Instant now = Instant.now();
            for (RoleAssignmentEntity assignment : assignments) {
                if (assignment.isActiveAt(now)) {
                    addAuthorities(authorities, assignment.getRole());
                }
            }
        }

        boolean accessGranted = isPlatformAdmin || currentMembership.isPresent() && !authorities.isEmpty();
        return new TenantAccess(organizationId, accessGranted, isPlatformAdmin, Collections.unmodifiableSet(authorities));
    }

    private void addAuthorities(Set<String> authorities, RoleEntity role) {
        authorities.add("ROLE_" + role.getCode());
        for (PermissionEntity permission : role.getPermissions()) {
            authorities.add(permission.getCode());
        }
    }

    public record TenantAccess(UUID organizationId, boolean granted, boolean platformAdmin, Set<String> authorities) {
    }
}
