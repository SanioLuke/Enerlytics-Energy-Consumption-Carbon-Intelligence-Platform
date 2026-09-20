package com.enerlytics.facility.infrastructure.persistence;

import com.enerlytics.facility.domain.BuildingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuildingRepository extends JpaRepository<BuildingEntity, UUID> {

    Optional<BuildingEntity> findByIdAndOrganizationIdAndActiveTrue(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndSiteIdAndBuildingCodeIgnoreCase(UUID organizationId, UUID siteId, String buildingCode);

    Page<BuildingEntity> findByOrganizationIdAndSiteIdAndActiveTrue(UUID organizationId, UUID siteId, Pageable pageable);

    Page<BuildingEntity> findByOrganizationIdAndSiteIdAndActiveTrueAndNameContainingIgnoreCaseOrBuildingCodeContainingIgnoreCase(
            UUID organizationId, UUID siteId, String nameQuery, String codeQuery, Pageable pageable);
}
