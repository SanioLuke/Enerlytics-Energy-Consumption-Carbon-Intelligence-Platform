package com.enerlytics.organization.application;

import com.enerlytics.organization.api.dto.OrganizationResponse;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(UUID id) {
        OrganizationEntity org = organizationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
        return toResponse(org);
    }

    public static OrganizationResponse toResponse(OrganizationEntity org) {
        return new OrganizationResponse(
                org.getId(),
                org.getOrganizationKey(),
                org.getDisplayName(),
                org.getStatus(),
                org.getDefaultCurrency()
        );
    }
}
