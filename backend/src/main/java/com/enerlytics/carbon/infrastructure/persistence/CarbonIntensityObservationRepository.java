package com.enerlytics.carbon.infrastructure.persistence;

import com.enerlytics.carbon.domain.CarbonIntensityObservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarbonIntensityObservationRepository extends JpaRepository<CarbonIntensityObservationEntity, Long> {

    Optional<CarbonIntensityObservationEntity> findFirstByProviderAndZoneOrderByObservedAtDesc(String provider, String zone);

    @Query("""
            SELECT o FROM CarbonIntensityObservationEntity o
            WHERE o.provider = :provider AND o.zone = :zone
              AND o.observedAt >= :from AND o.observedAt < :to
            ORDER BY o.observedAt
            """)
    List<CarbonIntensityObservationEntity> findByProviderAndZoneAndObservedAtBetween(
            @Param("provider") String provider,
            @Param("zone") String zone,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
