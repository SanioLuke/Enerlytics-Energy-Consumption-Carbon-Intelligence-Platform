package com.enerlytics.anomaly.application;

import com.enerlytics.anomaly.api.dto.MaintenanceWindowRequest;
import com.enerlytics.anomaly.api.dto.MaintenanceWindowResponse;
import com.enerlytics.anomaly.domain.MaintenanceWindowEntity;
import com.enerlytics.anomaly.infrastructure.persistence.MaintenanceWindowRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MaintenanceWindowService {

    private final MaintenanceWindowRepository repository;
    private final OrganizationRepository organizationRepository;

    public MaintenanceWindowService(MaintenanceWindowRepository repository,
                                    OrganizationRepository organizationRepository) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public MaintenanceWindowResponse create(UUID organizationId, MaintenanceWindowRequest request,
                                            String createdBy) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        if (request.entityType() == com.enerlytics.analytics.domain.DimensionType.ORGANIZATION
                && !organizationId.equals(request.entityId())) {
            throw new IllegalArgumentException("Organization entityId must match organizationId");
        }
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
        MaintenanceWindowEntity window = new MaintenanceWindowEntity(organization,
                request.entityType(), request.entityId(), request.startsAt(), request.endsAt(),
                request.reason(), createdBy);
        return toResponse(repository.save(window));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceWindowResponse> list(UUID organizationId) {
        return repository.findByOrganization_IdAndActiveTrueOrderByStartsAtDesc(organizationId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deactivate(UUID organizationId, UUID id) {
        MaintenanceWindowEntity window = repository.findByIdAndOrganization_Id(id, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance window not found"));
        window.deactivate();
        repository.save(window);
    }

    private MaintenanceWindowResponse toResponse(MaintenanceWindowEntity w) {
        return new MaintenanceWindowResponse(w.getId(), w.getDimensionType(), w.getDimensionId(),
                w.getStartsAt(), w.getEndsAt(), w.getReason(), w.isActive(), w.getCreatedBy());
    }
}
