package com.enerlytics.alert.infrastructure.persistence;

import com.enerlytics.alert.domain.AlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, UUID> {

    Optional<AlertRuleEntity> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    List<AlertRuleEntity> findByOrganization_IdAndEnabledTrue(UUID organizationId);
}
