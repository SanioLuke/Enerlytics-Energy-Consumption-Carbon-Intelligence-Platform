package com.enerlytics.anomaly.infrastructure.persistence;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.anomaly.domain.MaintenanceWindowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindowEntity, UUID> {

    Optional<MaintenanceWindowEntity> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    List<MaintenanceWindowEntity> findByOrganization_IdAndActiveTrueOrderByStartsAtDesc(UUID organizationId);

    @Query("""
            SELECT w FROM MaintenanceWindowEntity w
            WHERE w.organization.id = :organizationId
              AND w.active = true
              AND w.startsAt < :to
              AND w.endsAt > :from
              AND ((w.dimensionType = :dimensionType AND w.dimensionId = :dimensionId)
                   OR w.dimensionType = com.enerlytics.analytics.domain.DimensionType.ORGANIZATION)
            """)
    List<MaintenanceWindowEntity> findApplicable(
            @Param("organizationId") UUID organizationId,
            @Param("dimensionType") DimensionType dimensionType,
            @Param("dimensionId") UUID dimensionId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
