package com.enerlytics.facility.infrastructure.persistence;

import com.enerlytics.facility.domain.SiteEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, UUID> {

    Optional<SiteEntity> findByIdAndOrganizationIdAndActiveTrue(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndSiteCodeIgnoreCase(UUID organizationId, String siteCode);

    Page<SiteEntity> findByOrganizationIdAndActiveTrue(UUID organizationId, Pageable pageable);

    Page<SiteEntity> findByOrganizationIdAndActiveTrueAndNameContainingIgnoreCaseOrSiteCodeContainingIgnoreCase(
            UUID organizationId, String nameQuery, String codeQuery, Pageable pageable);
}
