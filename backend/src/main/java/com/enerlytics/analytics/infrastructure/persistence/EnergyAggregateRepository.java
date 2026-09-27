package com.enerlytics.analytics.infrastructure.persistence;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnergyAggregateRepository extends JpaRepository<EnergyAggregateEntity, UUID> {

    Optional<EnergyAggregateEntity> findByDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
            DimensionType dimensionType, UUID dimensionId, AggregationGranularity granularity, Instant bucketStart);

    List<EnergyAggregateEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId,
            AggregationGranularity granularity, Instant from, Instant to);
}
