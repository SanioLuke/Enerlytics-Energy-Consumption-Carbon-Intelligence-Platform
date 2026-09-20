package com.enerlytics.meter.infrastructure.persistence;

import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterRepository extends JpaRepository<MeterEntity, UUID>, JpaSpecificationExecutor<MeterEntity> {

    Optional<MeterEntity> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    boolean existsByOrganization_IdAndMeterCodeIgnoreCase(UUID organizationId, String meterCode);

    Page<MeterEntity> findByOrganization_Id(UUID organizationId, Pageable pageable);

    Page<MeterEntity> findByOrganization_IdAndSite_Id(UUID organizationId, UUID siteId, Pageable pageable);

    Page<MeterEntity> findByOrganization_IdAndBuilding_Id(UUID organizationId, UUID buildingId, Pageable pageable);

    Page<MeterEntity> findByOrganization_IdAndZone_Id(UUID organizationId, UUID zoneId, Pageable pageable);

    Page<MeterEntity> findByOrganization_IdAndStatus(UUID organizationId, MeterStatus status, Pageable pageable);

    Page<MeterEntity> findByOrganization_IdAndSite_IdAndStatus(
            UUID organizationId, UUID siteId, MeterStatus status, Pageable pageable);

    Page<MeterEntity> findByOrganization_IdAndMeterNameContainingIgnoreCaseOrMeterCodeContainingIgnoreCaseOrSerialNumberContainingIgnoreCase(
            UUID organizationId, String nameQuery, String codeQuery, String serialQuery, Pageable pageable);

    @Query("SELECT m FROM MeterEntity m JOIN FETCH m.site WHERE m.simulated = true AND m.status = :status")
    List<MeterEntity> findBySimulatedTrueAndStatus(@Param("status") MeterStatus status);
}
