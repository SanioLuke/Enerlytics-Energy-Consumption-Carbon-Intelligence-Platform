package com.enerlytics.alert.api;

import com.enerlytics.alert.api.dto.*;
import com.enerlytics.alert.application.AlertEvaluationService;
import com.enerlytics.alert.application.AlertLifecycleService;
import com.enerlytics.alert.application.AlertRuleService;
import com.enerlytics.alert.domain.AlertInstanceEntity;
import com.enerlytics.alert.domain.AlertStatus;
import com.enerlytics.alert.infrastructure.persistence.AlertInstanceRepository;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/alerts")
@Tag(name = "Alert Rules and Instances", description = "Alert-rule engine, evaluation, and lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

    private final AlertRuleService ruleService;
    private final AlertEvaluationService evaluationService;
    private final AlertLifecycleService lifecycleService;
    private final AlertInstanceRepository alertInstanceRepository;

    public AlertController(AlertRuleService ruleService,
                           AlertEvaluationService evaluationService,
                           AlertLifecycleService lifecycleService,
                           AlertInstanceRepository alertInstanceRepository) {
        this.ruleService = ruleService;
        this.evaluationService = evaluationService;
        this.lifecycleService = lifecycleService;
        this.alertInstanceRepository = alertInstanceRepository;
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<AlertRuleResponse> createRule(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateAlertRuleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.create(orgId, request));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('alert:read')")
    public ResponseEntity<List<AlertRuleResponse>> listRules(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(ruleService.list(orgId));
    }

    @GetMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('alert:read')")
    public ResponseEntity<AlertRuleResponse> getRule(
            @PathVariable UUID orgId,
            @PathVariable UUID ruleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(ruleService.get(orgId, ruleId));
    }

    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<AlertRuleResponse> updateRule(
            @PathVariable UUID orgId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody CreateAlertRuleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(ruleService.update(orgId, ruleId, request));
    }

    @PostMapping("/rules/{ruleId}/enable")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<Void> enableRule(
            @PathVariable UUID orgId,
            @PathVariable UUID ruleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        ruleService.toggle(orgId, ruleId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rules/{ruleId}/disable")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<Void> disableRule(
            @PathVariable UUID orgId,
            @PathVariable UUID ruleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        ruleService.toggle(orgId, ruleId, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<List<AlertInstanceResponse>> evaluate(
            @PathVariable UUID orgId,
            @RequestParam DimensionType scopeType,
            @RequestParam UUID scopeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant evaluationTime,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(evaluationService.evaluate(orgId, scopeType, scopeId, evaluationTime)
                .stream().map(this::toInstanceResponse).toList());
    }

    @PostMapping("/rules/{ruleId}/evaluate")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<AlertInstanceResponse> evaluateRule(
            @PathVariable UUID orgId,
            @PathVariable UUID ruleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant evaluationTime,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return evaluationService.evaluateRule(orgId, ruleId, evaluationTime)
                .map(i -> ResponseEntity.ok(toInstanceResponse(i)))
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('alert:read')")
    public ResponseEntity<List<AlertInstanceResponse>> listAlerts(
            @PathVariable UUID orgId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(queryAlerts(orgId, status));
    }

    @PostMapping("/{alertId}/acknowledge")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<AlertInstanceResponse> acknowledge(
            @PathVariable UUID orgId,
            @PathVariable UUID alertId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(toInstanceResponse(
                lifecycleService.acknowledge(orgId, alertId, principal.getEmail())));
    }

    @PostMapping("/{alertId}/resolve")
    @PreAuthorize("hasAuthority('alert:write')")
    public ResponseEntity<AlertInstanceResponse> resolve(
            @PathVariable UUID orgId,
            @PathVariable UUID alertId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TenantGuard.requireCurrentOrganizationOrPlatformAdmin(principal, orgId);
        return ResponseEntity.ok(toInstanceResponse(
                lifecycleService.resolve(orgId, alertId, principal.getEmail())));
    }

    private List<AlertInstanceResponse> queryAlerts(UUID orgId, String status) {
        if (status == null || status.isBlank()) {
            return alertInstanceRepository.findByOrganizationIdOrderByTriggeredAtDesc(orgId)
                    .stream().map(this::toInstanceResponse).toList();
        }
        return alertInstanceRepository.findByOrganizationIdAndStatusOrderByTriggeredAtDesc(
                        orgId, AlertStatus.valueOf(status.toUpperCase()))
                .stream().map(this::toInstanceResponse).toList();
    }

    private AlertInstanceResponse toInstanceResponse(AlertInstanceEntity i) {
        return new AlertInstanceResponse(i.getId(), i.getOrganizationId(), i.getRule().getId(),
                i.getRule().getName(), i.getScopeType(), i.getScopeId(), i.getAlertType().name(),
                i.getMetric(), i.getObservedValue(), i.getThreshold(), i.getSeverity().name(),
                i.getStatus().name(), i.getTriggeredAt(), i.getAcknowledgedAt(), i.getAcknowledgedBy(),
                i.getResolvedAt(), i.getResolvedBy(), i.getContextPayload());
    }
}
