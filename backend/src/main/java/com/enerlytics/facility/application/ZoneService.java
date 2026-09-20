package com.enerlytics.facility.application;

import com.enerlytics.common.api.PageResponse;
import com.enerlytics.common.api.PageableFactory;
import com.enerlytics.facility.api.dto.*;
import com.enerlytics.facility.domain.BuildingEntity;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.domain.ZoneEntity;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.facility.infrastructure.persistence.ZoneRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class ZoneService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "zoneCode", "createdAt", "updatedAt");

    private final ZoneRepository zoneRepository;
    private final SiteRepository siteRepository;
    private final BuildingRepository buildingRepository;

    public ZoneService(ZoneRepository zoneRepository, SiteRepository siteRepository, BuildingRepository buildingRepository) {
        this.zoneRepository = zoneRepository;
        this.siteRepository = siteRepository;
        this.buildingRepository = buildingRepository;
    }

    @Transactional
    public ZoneResponse create(UUID organizationId, UUID siteId, CreateZoneRequest request) {
        SiteEntity site = siteRepository.findByIdAndOrganizationIdAndActiveTrue(siteId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));

        BuildingEntity building = null;
        if (request.buildingId() != null) {
            building = buildingRepository.findByIdAndOrganizationIdAndActiveTrue(request.buildingId(), organizationId)
                    .filter(b -> b.getSite().getId().equals(siteId))
                    .orElseThrow(() -> new IllegalArgumentException("Building does not belong to the specified site"));
        }

        if (zoneRepository.existsByOrganizationIdAndSiteIdAndZoneCodeIgnoreCase(organizationId, siteId, request.code())) {
            throw new IllegalArgumentException("Zone code already exists in this site");
        }

        ZoneEntity zone = new ZoneEntity(site.getOrganization(), site, building, request.code(), request.name());
        applyRequest(zone, request);
        return toResponse(zoneRepository.save(zone));
    }

    @Transactional(readOnly = true)
    public ZoneResponse get(UUID organizationId, UUID siteId, UUID zoneId) {
        return toResponse(findZone(organizationId, siteId, zoneId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ZoneResponse> list(UUID organizationId, UUID siteId, UUID buildingId, String search, Integer page, Integer size, String[] sort) {
        Pageable pageable = PageableFactory.create(page, size, sort, ALLOWED_SORT_FIELDS);
        Page<ZoneEntity> result;
        if (search != null && !search.isBlank()) {
            String query = search.trim();
            result = zoneRepository.findByOrganizationIdAndSiteIdAndActiveTrueAndNameContainingIgnoreCaseOrZoneCodeContainingIgnoreCase(
                    organizationId, siteId, query, query, pageable);
        } else if (buildingId != null) {
            result = zoneRepository.findByOrganizationIdAndSiteIdAndBuildingIdAndActiveTrue(organizationId, siteId, buildingId, pageable);
        } else {
            result = zoneRepository.findByOrganizationIdAndSiteIdAndActiveTrue(organizationId, siteId, pageable);
        }
        return new PageResponse<>(result.map(this::toResponse).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ZoneResponse update(UUID organizationId, UUID siteId, UUID zoneId, UpdateZoneRequest request) {
        ZoneEntity zone = findZone(organizationId, siteId, zoneId);
        if (request.buildingId() != null) {
            BuildingEntity building = buildingRepository.findByIdAndOrganizationIdAndActiveTrue(request.buildingId(), organizationId)
                    .filter(b -> b.getSite().getId().equals(siteId))
                    .orElseThrow(() -> new IllegalArgumentException("Building does not belong to the specified site"));
            zone.setBuilding(building);
        } else {
            zone.setBuilding(null);
        }
        applyRequest(zone, request);
        return toResponse(zoneRepository.save(zone));
    }

    @Transactional
    public void delete(UUID organizationId, UUID siteId, UUID zoneId) {
        ZoneEntity zone = findZone(organizationId, siteId, zoneId);
        zone.archive();
        zoneRepository.save(zone);
    }

    private ZoneEntity findZone(UUID organizationId, UUID siteId, UUID zoneId) {
        return zoneRepository.findByIdAndOrganizationIdAndActiveTrue(zoneId, organizationId)
                .filter(z -> z.getSite().getId().equals(siteId))
                .orElseThrow(() -> new EntityNotFoundException("Zone not found"));
    }

    private void applyRequest(ZoneEntity zone, CreateZoneRequest request) {
        zone.setName(request.name());
        zone.setDescription(request.description());
        zone.setZoneType(request.zoneType());
    }

    private void applyRequest(ZoneEntity zone, UpdateZoneRequest request) {
        if (request.name() != null) zone.setName(request.name());
        if (request.description() != null) zone.setDescription(request.description());
        if (request.zoneType() != null) zone.setZoneType(request.zoneType());
    }

    public ZoneResponse toResponse(ZoneEntity zone) {
        return new ZoneResponse(
                zone.getId(),
                zone.getOrganization().getId(),
                zone.getSite().getId(),
                zone.getBuilding() != null ? zone.getBuilding().getId() : null,
                zone.getZoneCode(),
                zone.getName(),
                zone.getDescription(),
                zone.getZoneType(),
                zone.isActive(),
                zone.getCreatedAt(),
                zone.getUpdatedAt(),
                zone.getVersion()
        );
    }
}
