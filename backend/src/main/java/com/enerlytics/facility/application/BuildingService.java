package com.enerlytics.facility.application;

import com.enerlytics.common.api.PageResponse;
import com.enerlytics.common.api.PageableFactory;
import com.enerlytics.facility.api.dto.*;
import com.enerlytics.facility.domain.BuildingEntity;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class BuildingService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "buildingCode", "createdAt", "updatedAt");

    private final BuildingRepository buildingRepository;
    private final SiteRepository siteRepository;

    public BuildingService(BuildingRepository buildingRepository, SiteRepository siteRepository) {
        this.buildingRepository = buildingRepository;
        this.siteRepository = siteRepository;
    }

    @Transactional
    public BuildingResponse create(UUID organizationId, UUID siteId, CreateBuildingRequest request) {
        SiteEntity site = siteRepository.findByIdAndOrganizationIdAndActiveTrue(siteId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));

        if (buildingRepository.existsByOrganizationIdAndSiteIdAndBuildingCodeIgnoreCase(organizationId, siteId, request.code())) {
            throw new IllegalArgumentException("Building code already exists in this site");
        }

        BuildingEntity building = new BuildingEntity(site.getOrganization(), site, request.code(), request.name());
        applyRequest(building, request);
        return toResponse(buildingRepository.save(building));
    }

    @Transactional(readOnly = true)
    public BuildingResponse get(UUID organizationId, UUID siteId, UUID buildingId) {
        return toResponse(findBuilding(organizationId, siteId, buildingId));
    }

    @Transactional(readOnly = true)
    public PageResponse<BuildingResponse> list(UUID organizationId, UUID siteId, String search, Integer page, Integer size, String[] sort) {
        Pageable pageable = PageableFactory.create(page, size, sort, ALLOWED_SORT_FIELDS);
        Page<BuildingEntity> result;
        if (search != null && !search.isBlank()) {
            String query = search.trim();
            result = buildingRepository.findByOrganizationIdAndSiteIdAndActiveTrueAndNameContainingIgnoreCaseOrBuildingCodeContainingIgnoreCase(
                    organizationId, siteId, query, query, pageable);
        } else {
            result = buildingRepository.findByOrganizationIdAndSiteIdAndActiveTrue(organizationId, siteId, pageable);
        }
        return new PageResponse<>(result.map(this::toResponse).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public BuildingResponse update(UUID organizationId, UUID siteId, UUID buildingId, UpdateBuildingRequest request) {
        BuildingEntity building = findBuilding(organizationId, siteId, buildingId);
        applyRequest(building, request);
        return toResponse(buildingRepository.save(building));
    }

    @Transactional
    public void delete(UUID organizationId, UUID siteId, UUID buildingId) {
        BuildingEntity building = findBuilding(organizationId, siteId, buildingId);
        building.archive();
        buildingRepository.save(building);
    }

    private BuildingEntity findBuilding(UUID organizationId, UUID siteId, UUID buildingId) {
        return buildingRepository.findByIdAndOrganizationIdAndActiveTrue(buildingId, organizationId)
                .filter(b -> b.getSite().getId().equals(siteId))
                .orElseThrow(() -> new EntityNotFoundException("Building not found"));
    }

    private void applyRequest(BuildingEntity building, CreateBuildingRequest request) {
        building.setName(request.name());
        building.setDescription(request.description());
        building.setFloorArea(request.floorArea());
        building.setFloorAreaUnit(request.floorAreaUnit());
        building.setBuildingType(request.buildingType());
        building.setCommissionedDate(request.commissionedDate());
    }

    private void applyRequest(BuildingEntity building, UpdateBuildingRequest request) {
        if (request.name() != null) building.setName(request.name());
        if (request.description() != null) building.setDescription(request.description());
        if (request.floorArea() != null) building.setFloorArea(request.floorArea());
        if (request.floorAreaUnit() != null) building.setFloorAreaUnit(request.floorAreaUnit());
        if (request.buildingType() != null) building.setBuildingType(request.buildingType());
        if (request.commissionedDate() != null) building.setCommissionedDate(request.commissionedDate());
    }

    public BuildingResponse toResponse(BuildingEntity building) {
        return new BuildingResponse(
                building.getId(),
                building.getOrganization().getId(),
                building.getSite().getId(),
                building.getBuildingCode(),
                building.getName(),
                building.getDescription(),
                building.getFloorArea(),
                building.getFloorAreaUnit(),
                building.getBuildingType(),
                building.getCommissionedDate(),
                building.isActive(),
                building.getCreatedAt(),
                building.getUpdatedAt(),
                building.getVersion()
        );
    }
}
