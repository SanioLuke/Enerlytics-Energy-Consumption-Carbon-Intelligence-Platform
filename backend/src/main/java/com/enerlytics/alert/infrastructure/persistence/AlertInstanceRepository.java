package com.enerlytics.alert.infrastructure.persistence;

import com.enerlytics.alert.domain.AlertInstanceEntity;
import com.enerlytics.alert.domain.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertInstanceRepository extends JpaRepository<AlertInstanceEntity, UUID> {

    Optional<AlertInstanceEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            SELECT a FROM AlertInstanceEntity a
            WHERE a.organizationId = :organizationId
              AND a.rule.id = :ruleId
              AND a.scopeId = :scopeId
              AND a.status IN (com.enerlytics.alert.domain.AlertStatus.OPEN, com.enerlytics.alert.domain.AlertStatus.ACKNOWLEDGED)
              AND a.triggeredAt >= :since
            ORDER BY a.triggeredAt DESC
            """)
    List<AlertInstanceEntity> findRecentActiveByRuleAndScope(
            @Param("organizationId") UUID organizationId,
            @Param("ruleId") UUID ruleId,
            @Param("scopeId") UUID scopeId,
            @Param("since") Instant since);

    List<AlertInstanceEntity> findByOrganizationIdOrderByTriggeredAtDesc(UUID organizationId);

    List<AlertInstanceEntity> findByOrganizationIdAndStatusOrderByTriggeredAtDesc(UUID organizationId, AlertStatus status);

    List<AlertInstanceEntity> findByOrganizationIdAndRule_IdOrderByTriggeredAtDesc(UUID organizationId, UUID ruleId);

    long countByOrganizationIdAndScopeTypeAndScopeIdAndTriggeredAtBetween(
            UUID organizationId, com.enerlytics.analytics.domain.DimensionType scopeType,
            UUID scopeId, Instant from, Instant to);
}
