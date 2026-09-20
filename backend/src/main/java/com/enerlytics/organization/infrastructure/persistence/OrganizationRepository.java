package com.enerlytics.organization.infrastructure.persistence;

import com.enerlytics.organization.domain.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    Optional<OrganizationEntity> findByOrganizationKey(String organizationKey);
}
