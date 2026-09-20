package com.enerlytics.facility.application;

import com.enerlytics.common.api.PageResponse;
import com.enerlytics.common.api.PageableFactory;
import com.enerlytics.facility.api.dto.CreateSiteRequest;
import com.enerlytics.facility.api.dto.SiteResponse;
import com.enerlytics.facility.api.dto.UpdateSiteRequest;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

@Service
public class SiteService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "siteCode", "createdAt", "updatedAt");

    private final SiteRepository siteRepository;
    private final OrganizationRepository organizationRepository;

    public SiteService(SiteRepository siteRepository, OrganizationRepository organizationRepository) {
        this.siteRepository = siteRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public SiteResponse create(UUID organizationId, CreateSiteRequest request) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));

        if (siteRepository.existsByOrganizationIdAndSiteCodeIgnoreCase(organizationId, request.code())) {
            throw new IllegalArgumentException("Site code already exists in this organization");
        }
        validateTimezone(request.timezone());
        validateDateRange(request.openedOn(), request.closedOn());

        SiteEntity site = new SiteEntity(organization, request.code(), request.name(), request.timezone());
        applyRequest(site, request);
        SiteEntity saved = siteRepository.save(site);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SiteResponse get(UUID organizationId, UUID siteId) {
        SiteEntity site = findSite(organizationId, siteId);
        return toResponse(site);
    }

    @Transactional(readOnly = true)
    public PageResponse<SiteResponse> list(UUID organizationId, String search, Integer page, Integer size, String[] sort) {
        Pageable pageable = PageableFactory.create(page, size, sort, ALLOWED_SORT_FIELDS);
        Page<SiteEntity> result;
        if (search != null && !search.isBlank()) {
            String query = search.trim();
            result = siteRepository.findByOrganizationIdAndActiveTrueAndNameContainingIgnoreCaseOrSiteCodeContainingIgnoreCase(
                    organizationId, query, query, pageable);
        } else {
            result = siteRepository.findByOrganizationIdAndActiveTrue(organizationId, pageable);
        }
        return new PageResponse<>(result.map(this::toResponse).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public SiteResponse update(UUID organizationId, UUID siteId, UpdateSiteRequest request) {
        SiteEntity site = findSite(organizationId, siteId);
        validateTimezone(request.timezone());
        validateDateRange(request.openedOn(), request.closedOn());
        applyRequest(site, request);
        return toResponse(siteRepository.save(site));
    }

    @Transactional
    public void delete(UUID organizationId, UUID siteId) {
        SiteEntity site = findSite(organizationId, siteId);
        site.archive();
        siteRepository.save(site);
    }

    private SiteEntity findSite(UUID organizationId, UUID siteId) {
        return siteRepository.findByIdAndOrganizationIdAndActiveTrue(siteId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));
    }

    private void validateTimezone(String timezone) {
        if (timezone != null && !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new IllegalArgumentException("Invalid IANA timezone: " + timezone);
        }
    }

    private void validateDateRange(java.time.LocalDate openedOn, java.time.LocalDate closedOn) {
        if (openedOn != null && closedOn != null && closedOn.isBefore(openedOn)) {
            throw new IllegalArgumentException("Closed date must be on or after opened date");
        }
    }

    private void applyRequest(SiteEntity site, CreateSiteRequest request) {
        site.setName(request.name());
        site.setDescription(request.description());
        site.setAddress(request.address());
        site.setCountry(request.country());
        site.setState(request.state());
        site.setCity(request.city());
        site.setPostalCode(request.postalCode());
        site.setLatitude(request.latitude());
        site.setLongitude(request.longitude());
        site.setIanaTimezone(request.timezone());
        site.setGridRegionCode(request.gridRegionCode());
        if (request.currency() != null) {
            site.setCurrency(request.currency().toUpperCase());
        }
        site.setFloorArea(request.floorArea());
        site.setFloorAreaUnit(request.floorAreaUnit());
        site.setOpenedOn(request.openedOn());
        site.setClosedOn(request.closedOn());
    }

    private void applyRequest(SiteEntity site, UpdateSiteRequest request) {
        if (request.name() != null) site.setName(request.name());
        if (request.description() != null) site.setDescription(request.description());
        if (request.address() != null) site.setAddress(request.address());
        if (request.country() != null) site.setCountry(request.country());
        if (request.state() != null) site.setState(request.state());
        if (request.city() != null) site.setCity(request.city());
        if (request.postalCode() != null) site.setPostalCode(request.postalCode());
        if (request.latitude() != null) site.setLatitude(request.latitude());
        if (request.longitude() != null) site.setLongitude(request.longitude());
        if (request.timezone() != null) site.setIanaTimezone(request.timezone());
        if (request.gridRegionCode() != null) site.setGridRegionCode(request.gridRegionCode());
        if (request.currency() != null) site.setCurrency(request.currency().toUpperCase());
        if (request.floorArea() != null) site.setFloorArea(request.floorArea());
        if (request.floorAreaUnit() != null) site.setFloorAreaUnit(request.floorAreaUnit());
        if (request.openedOn() != null) site.setOpenedOn(request.openedOn());
        if (request.closedOn() != null) site.setClosedOn(request.closedOn());
    }

    public SiteResponse toResponse(SiteEntity site) {
        return new SiteResponse(
                site.getId(),
                site.getOrganization().getId(),
                site.getSiteCode(),
                site.getName(),
                site.getDescription(),
                site.getAddress(),
                site.getCountry(),
                site.getState(),
                site.getCity(),
                site.getPostalCode(),
                site.getLatitude(),
                site.getLongitude(),
                site.getIanaTimezone(),
                site.getGridRegionCode(),
                site.getCurrency(),
                site.getFloorArea(),
                site.getFloorAreaUnit(),
                site.isActive(),
                site.getOpenedOn(),
                site.getClosedOn(),
                site.getCreatedAt(),
                site.getUpdatedAt(),
                site.getVersion()
        );
    }
}
