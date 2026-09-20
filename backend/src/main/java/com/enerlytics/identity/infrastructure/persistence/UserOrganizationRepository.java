package com.enerlytics.identity.infrastructure.persistence;

import com.enerlytics.identity.domain.UserOrganizationEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserOrganizationRepository extends JpaRepository<UserOrganizationEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "user"})
    List<UserOrganizationEntity> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"organization", "user"})
    Optional<UserOrganizationEntity> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);
}
