package com.enerlytics.meter.infrastructure.persistence;

import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterRepository extends JpaRepository<MeterEntity, UUID>, JpaSpecificationExecutor<MeterEntity> {

    Optional<MeterEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndMeterCodeIgnoreCase(UUID organizationId, String meterCode);

    Page<MeterEntity> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<MeterEntity> findByOrganizationIdAndSiteId(UUID organizationId, UUID siteId, Pageable pageable);

    Page<MeterEntity> findByOrganizationIdAndBuildingId(UUID organizationId, UUID buildingId, Pageable pageable);

    Page<MeterEntity> findByOrganizationIdAndZoneId(UUID organizationId, UUID zoneId, Pageable pageable);

    Page<MeterEntity> findByOrganizationIdAndStatus(UUID organizationId, MeterStatus status, Pageable pageable);

    Page<MeterEntity> findByOrganizationIdAndSiteIdAndStatus(
            UUID organizationId, UUID siteId, MeterStatus status, Pageable pageable);

    Page<MeterEntity> findByOrganizationIdAndMeterNameContainingIgnoreCaseOrMeterCodeContainingIgnoreCaseOrSerialNumberContainingIgnoreCase(
            UUID organizationId, String nameQuery, String codeQuery, String serialQuery, Pageable pageable);
}
