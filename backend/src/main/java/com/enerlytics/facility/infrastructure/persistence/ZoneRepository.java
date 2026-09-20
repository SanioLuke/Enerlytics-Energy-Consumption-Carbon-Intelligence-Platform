package com.enerlytics.facility.infrastructure.persistence;

import com.enerlytics.facility.domain.ZoneEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ZoneRepository extends JpaRepository<ZoneEntity, UUID> {

    Optional<ZoneEntity> findByIdAndOrganizationIdAndActiveTrue(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndSiteIdAndZoneCodeIgnoreCase(UUID organizationId, UUID siteId, String zoneCode);

    Page<ZoneEntity> findByOrganizationIdAndSiteIdAndBuildingIdAndActiveTrue(
            UUID organizationId, UUID siteId, UUID buildingId, Pageable pageable);

    Page<ZoneEntity> findByOrganizationIdAndSiteIdAndActiveTrue(
            UUID organizationId, UUID siteId, Pageable pageable);

    Page<ZoneEntity> findByOrganizationIdAndSiteIdAndActiveTrueAndNameContainingIgnoreCaseOrZoneCodeContainingIgnoreCase(
            UUID organizationId, UUID siteId, String nameQuery, String codeQuery, Pageable pageable);
}
