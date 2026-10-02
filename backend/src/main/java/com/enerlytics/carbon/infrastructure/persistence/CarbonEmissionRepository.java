package com.enerlytics.carbon.infrastructure.persistence;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.carbon.domain.CarbonEmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarbonEmissionRepository extends JpaRepository<CarbonEmissionEntity, UUID> {

    Optional<CarbonEmissionEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId,
            AggregationGranularity granularity, Instant bucketStart);

    List<CarbonEmissionEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId,
            AggregationGranularity granularity, Instant from, Instant to);

    @Query("""
            SELECT e FROM CarbonEmissionEntity e
            WHERE e.organizationId = :orgId
              AND e.dimensionType = :dimType
              AND e.bucketStart >= :from AND e.bucketStart < :to
            ORDER BY e.bucketStart
            """)
    List<CarbonEmissionEntity> findByOrganizationIdAndDimensionTypeAndBucketStartBetween(
            @Param("orgId") UUID organizationId,
            @Param("dimType") DimensionType dimensionType,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT e FROM CarbonEmissionEntity e
            WHERE e.organizationId = :orgId
              AND e.dimensionType = 'SITE'
              AND e.granularity = :granularity
              AND e.bucketStart >= :from AND e.bucketStart < :to
            ORDER BY e.emissionsKgCo2Eq DESC
            """)
    List<CarbonEmissionEntity> findTopSiteEmissions(
            @Param("orgId") UUID organizationId,
            @Param("granularity") AggregationGranularity granularity,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
