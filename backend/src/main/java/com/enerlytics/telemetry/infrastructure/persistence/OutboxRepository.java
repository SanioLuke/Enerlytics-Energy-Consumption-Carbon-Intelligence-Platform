package com.enerlytics.telemetry.infrastructure.persistence;

import com.enerlytics.telemetry.domain.OutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    List<OutboxEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    @Modifying
    @Query("UPDATE OutboxEntity o SET o.publishedAt = :publishedAt WHERE o.id = :id AND o.publishedAt IS NULL")
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
}
