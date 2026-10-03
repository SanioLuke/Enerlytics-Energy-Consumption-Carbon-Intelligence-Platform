package com.enerlytics.anomaly.infrastructure.persistence;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.anomaly.domain.AnomalyMethod;
import com.enerlytics.anomaly.domain.DetectedAnomalyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DetectedAnomalyRepository extends JpaRepository<DetectedAnomalyEntity, UUID> {

    Optional<DetectedAnomalyEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdAndBucketStartAndMethod(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId,
            Instant bucketStart, AnomalyMethod method);

    List<DetectedAnomalyEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdAndBucketStartBetweenOrderByBucketStartDesc(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId, Instant from, Instant to);

    List<DetectedAnomalyEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdAndBucketStartBetweenAndSuppressedFalseOrderByBucketStartDesc(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId, Instant from, Instant to);

    List<DetectedAnomalyEntity> findByOrganizationIdAndBucketStartBetweenAndSuppressedFalseOrderByBucketStartDesc(
            UUID organizationId, Instant from, Instant to);
}
