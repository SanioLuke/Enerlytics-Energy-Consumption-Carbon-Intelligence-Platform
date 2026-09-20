package com.enerlytics.telemetry.infrastructure.persistence;

import com.enerlytics.telemetry.domain.MeterReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReadingEntity, UUID> {
    boolean existsBySourceEventId(UUID sourceEventId);
}
