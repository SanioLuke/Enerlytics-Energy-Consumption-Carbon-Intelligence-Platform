package com.enerlytics.telemetry.infrastructure.persistence;

import com.enerlytics.telemetry.domain.MeterReadingRejectedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MeterReadingRejectedRepository extends JpaRepository<MeterReadingRejectedEntity, UUID> {
    boolean existsBySourceEventId(UUID sourceEventId);
}
