package com.enerlytics.identity.infrastructure.persistence;

import com.enerlytics.identity.domain.RoleAssignmentEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignmentEntity, UUID> {

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    List<RoleAssignmentEntity> findByUserOrganizationId(UUID userOrganizationId);
}
