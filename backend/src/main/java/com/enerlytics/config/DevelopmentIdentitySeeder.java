package com.enerlytics.config;

import com.enerlytics.identity.application.RoleAssignmentService;
import com.enerlytics.identity.domain.MembershipStatus;
import com.enerlytics.identity.domain.UserEntity;
import com.enerlytics.identity.domain.UserOrganizationEntity;
import com.enerlytics.identity.infrastructure.persistence.UserOrganizationRepository;
import com.enerlytics.identity.infrastructure.persistence.UserRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "dev"})
public class DevelopmentIdentitySeeder {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentIdentitySeeder.class);

    private final DevAdminProperties properties;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final RoleAssignmentService roleAssignmentService;
    private final PasswordEncoder passwordEncoder;

    public DevelopmentIdentitySeeder(DevAdminProperties properties,
                                    OrganizationRepository organizationRepository,
                                    UserRepository userRepository,
                                    UserOrganizationRepository userOrganizationRepository,
                                    RoleAssignmentService roleAssignmentService,
                                    PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.roleAssignmentService = roleAssignmentService;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!hasRequiredProperties()) {
            log.info("Development admin seeding skipped (no credentials configured).");
            return;
        }

        String normalizedEmail = properties.email().toLowerCase();
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            log.info("Development admin account already exists.");
            return;
        }

        OrganizationEntity organization = organizationRepository.findByOrganizationKey(properties.organizationKey())
                .orElseGet(() -> {
                    log.info("Creating development organization: {}", properties.organizationKey());
                    return organizationRepository.save(
                            new OrganizationEntity(properties.organizationKey(), properties.organizationName()));
                });

        UserEntity user = new UserEntity(properties.email(), properties.displayName());
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setExternalSubject(normalizedEmail);
        user = userRepository.save(user);

        UserOrganizationEntity membership = new UserOrganizationEntity(organization, user);
        membership.setMembershipStatus(MembershipStatus.ACTIVE);
        membership = userOrganizationRepository.save(membership);

        roleAssignmentService.assignRole(organization, membership, "PLATFORM_ADMIN");

        log.info("Seeded development admin {} in organization {}", normalizedEmail, organization.getOrganizationKey());
    }

    private boolean hasRequiredProperties() {
        return properties.email() != null && !properties.email().isBlank()
                && properties.password() != null && !properties.password().isBlank();
    }
}
