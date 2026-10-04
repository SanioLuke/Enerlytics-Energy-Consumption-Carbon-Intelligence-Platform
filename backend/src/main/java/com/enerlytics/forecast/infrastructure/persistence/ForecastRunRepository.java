package com.enerlytics.forecast.infrastructure.persistence;

import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.forecast.domain.ForecastHorizon;
import com.enerlytics.forecast.domain.ForecastRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForecastRunRepository extends JpaRepository<ForecastRunEntity, UUID> {

    Optional<ForecastRunEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<ForecastRunEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdOrderByGeneratedAtDesc(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId);

    List<ForecastRunEntity> findByOrganizationIdAndDimensionTypeAndDimensionIdAndHorizonOrderByGeneratedAtDesc(
            UUID organizationId, DimensionType dimensionType, UUID dimensionId, ForecastHorizon horizon);
}
