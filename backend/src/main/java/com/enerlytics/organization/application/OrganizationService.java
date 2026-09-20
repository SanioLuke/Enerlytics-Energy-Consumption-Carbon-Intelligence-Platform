package com.enerlytics.organization.application;

import com.enerlytics.organization.api.dto.CreateOrganizationRequest;
import com.enerlytics.organization.api.dto.OrganizationResponse;
import com.enerlytics.organization.api.dto.UpdateOrganizationRequest;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.domain.OrganizationStatus;
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

    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        if (organizationRepository.findByOrganizationKey(request.organizationKey()).isPresent()) {
            throw new IllegalArgumentException("Organization key already exists: " + request.organizationKey());
        }
        OrganizationEntity org = new OrganizationEntity(request.organizationKey(), request.displayName());
        org.setDefaultCurrency(request.defaultCurrency());
        org.setFiscalYearStartMonth(request.fiscalYearStartMonth());
        org.setLocale(request.locale());
        org.setStatus(request.status());
        return toResponse(organizationRepository.save(org));
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(UUID id) {
        OrganizationEntity org = organizationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
        return toResponse(org);
    }

    @Transactional
    public OrganizationResponse update(UUID id, UpdateOrganizationRequest request) {
        OrganizationEntity org = organizationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
        if (request.displayName() != null) org.setDisplayName(request.displayName());
        if (request.defaultCurrency() != null) org.setDefaultCurrency(request.defaultCurrency());
        if (request.fiscalYearStartMonth() != null) org.setFiscalYearStartMonth(request.fiscalYearStartMonth());
        if (request.locale() != null) org.setLocale(request.locale());
        if (request.status() != null) {
            if (request.status() == OrganizationStatus.ARCHIVED) {
                org.archive();
            } else {
                org.setStatus(request.status());
                org.setArchivedAt(null);
            }
        }
        return toResponse(organizationRepository.save(org));
    }

    @Transactional
    public void delete(UUID id) {
        OrganizationEntity org = organizationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));
        org.archive();
        organizationRepository.save(org);
    }

    public static OrganizationResponse toResponse(OrganizationEntity org) {
        return new OrganizationResponse(
                org.getId(),
                org.getOrganizationKey(),
                org.getDisplayName(),
                org.getStatus(),
                org.getDefaultCurrency(),
                org.getFiscalYearStartMonth(),
                org.getLocale(),
                org.getArchivedAt()
        );
    }
}
