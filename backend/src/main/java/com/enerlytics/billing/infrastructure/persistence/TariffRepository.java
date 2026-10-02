package com.enerlytics.billing.infrastructure.persistence;

import com.enerlytics.billing.domain.TariffEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TariffRepository extends JpaRepository<TariffEntity, UUID> {

    Optional<TariffEntity> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    List<TariffEntity> findByOrganization_IdAndSite_IdAndActiveTrueOrderByEffectiveFromDesc(
            UUID organizationId, UUID siteId);

    List<TariffEntity> findBySite_IdAndActiveTrue(UUID siteId);

    @Query("""
            SELECT t FROM TariffEntity t
            JOIN FETCH t.rates
            WHERE t.site.id = :siteId
              AND t.active = true
              AND t.effectiveFrom <= :date
              AND (t.effectiveTo IS NULL OR t.effectiveTo >= :date)
            ORDER BY t.effectiveFrom DESC
            """)
    List<TariffEntity> findEffectiveForSite(@Param("siteId") UUID siteId, @Param("date") LocalDate date);
}
