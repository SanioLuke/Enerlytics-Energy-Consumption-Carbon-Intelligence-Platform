package com.enerlytics.alert.application;

import com.enerlytics.alert.api.dto.*;
import com.enerlytics.alert.domain.AlertRuleEntity;
import com.enerlytics.alert.domain.AlertSeverity;
import com.enerlytics.alert.domain.AlertType;
import com.enerlytics.alert.domain.ComparisonOperator;
import com.enerlytics.alert.infrastructure.persistence.AlertRuleRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final OrganizationRepository organizationRepository;

    public AlertRuleService(AlertRuleRepository alertRuleRepository,
                            OrganizationRepository organizationRepository) {
        this.alertRuleRepository = alertRuleRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public AlertRuleResponse create(UUID organizationId, CreateAlertRuleRequest request) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
        AlertRuleEntity entity = new AlertRuleEntity(organization, request.name(),
                AlertType.valueOf(request.alertType()), request.scopeType(), request.scopeId(),
                request.metric(), ComparisonOperator.valueOf(request.comparisonOperator()),
                request.threshold(), request.evaluationWindowSeconds(),
                AlertSeverity.valueOf(request.severity()), request.cooldownSeconds());
        return toResponse(alertRuleRepository.save(entity));
    }

    @Transactional
    public AlertRuleResponse update(UUID organizationId, UUID ruleId, CreateAlertRuleRequest request) {
        AlertRuleEntity entity = alertRuleRepository.findByIdAndOrganization_Id(ruleId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found"));
        entity.setName(request.name());
        entity.setAlertType(AlertType.valueOf(request.alertType()));
        entity.setScopeType(request.scopeType());
        entity.setScopeId(request.scopeId());
        entity.setMetric(request.metric());
        entity.setComparisonOperator(ComparisonOperator.valueOf(request.comparisonOperator()));
        entity.setThreshold(request.threshold());
        entity.setEvaluationWindowSeconds(request.evaluationWindowSeconds());
        entity.setSeverity(AlertSeverity.valueOf(request.severity()));
        entity.setCooldownSeconds(request.cooldownSeconds());
        entity.setEnabled(request.enabled());
        return toResponse(alertRuleRepository.save(entity));
    }

    @Transactional
    public void toggle(UUID organizationId, UUID ruleId, boolean enabled) {
        AlertRuleEntity entity = alertRuleRepository.findByIdAndOrganization_Id(ruleId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found"));
        entity.setEnabled(enabled);
        alertRuleRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> list(UUID organizationId) {
        return alertRuleRepository.findByOrganization_IdAndEnabledTrue(organizationId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AlertRuleResponse get(UUID organizationId, UUID ruleId) {
        return toResponse(alertRuleRepository.findByIdAndOrganization_Id(ruleId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Alert rule not found")));
    }

    private AlertRuleResponse toResponse(AlertRuleEntity e) {
        return new AlertRuleResponse(e.getId(), e.getOrganization().getId(), e.getName(),
                e.getAlertType().name(), e.getScopeType(), e.getScopeId(), e.getMetric(),
                e.getComparisonOperator().name(), e.getThreshold(), e.getEvaluationWindowSeconds(),
                e.getSeverity().name(), e.getCooldownSeconds(), e.isEnabled(), e.getVersion());
    }
}
