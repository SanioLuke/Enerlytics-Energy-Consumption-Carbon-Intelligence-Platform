package com.enerlytics.billing.infrastructure.persistence;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.billing.domain.EnergyCostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnergyCostRepository extends JpaRepository<EnergyCostEntity, UUID> {

    Optional<EnergyCostEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId,
            AggregationGranularity granularity, Instant bucketStart);

    List<EnergyCostEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId,
            AggregationGranularity granularity, Instant from, Instant to);
}
