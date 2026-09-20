package com.enerlytics.identity.application;

import com.enerlytics.identity.domain.*;
import com.enerlytics.identity.infrastructure.persistence.*;
import com.enerlytics.organization.domain.OrganizationEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAssignmentService {

    private final RoleRepository roleRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public RoleAssignmentService(RoleRepository roleRepository,
                                 UserOrganizationRepository userOrganizationRepository,
                                 RoleAssignmentRepository roleAssignmentRepository) {
        this.roleRepository = roleRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Transactional
    public RoleAssignmentEntity assignRole(OrganizationEntity organization,
                                           UserOrganizationEntity membership,
                                           String roleCode) {
        RoleEntity role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleCode));
        RoleAssignmentEntity assignment = new RoleAssignmentEntity(organization, membership, role);
        return roleAssignmentRepository.save(assignment);
    }

    @Transactional
    public UserOrganizationEntity ensureMembership(OrganizationEntity organization, UserEntity user) {
        return userOrganizationRepository.findByUserIdAndOrganizationId(user.getId(), organization.getId())
                .orElseGet(() -> {
                    UserOrganizationEntity membership = new UserOrganizationEntity(organization, user);
                    membership.setMembershipStatus(MembershipStatus.ACTIVE);
                    return userOrganizationRepository.save(membership);
                });
    }
}
