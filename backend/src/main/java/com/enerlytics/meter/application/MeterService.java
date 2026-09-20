package com.enerlytics.meter.application;

import com.enerlytics.common.api.PageResponse;
import com.enerlytics.common.api.PageableFactory;
import com.enerlytics.facility.domain.BuildingEntity;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.domain.ZoneEntity;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.facility.infrastructure.persistence.ZoneRepository;
import com.enerlytics.meter.api.dto.*;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.meter.infrastructure.persistence.MeterSpecification;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MeterService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name", "meterCode", "status", "createdAt", "updatedAt");

    private final MeterRepository meterRepository;
    private final OrganizationRepository organizationRepository;
    private final SiteRepository siteRepository;
    private final BuildingRepository buildingRepository;
    private final ZoneRepository zoneRepository;
    private final ObjectMapper objectMapper;

    public MeterService(MeterRepository meterRepository, OrganizationRepository organizationRepository,
                        SiteRepository siteRepository, BuildingRepository buildingRepository,
                        ZoneRepository zoneRepository, ObjectMapper objectMapper) {
        this.meterRepository = meterRepository;
        this.organizationRepository = organizationRepository;
        this.siteRepository = siteRepository;
        this.buildingRepository = buildingRepository;
        this.zoneRepository = zoneRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MeterResponse create(UUID organizationId, CreateMeterRequest request) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found"));

        if (meterRepository.existsByOrganizationIdAndMeterCodeIgnoreCase(organizationId, request.code())) {
            throw new IllegalArgumentException("Meter code already exists in this organization");
        }

        SiteEntity site = resolveSite(organizationId, request.siteId());
        BuildingEntity building = request.buildingId() != null ? resolveBuilding(organizationId, request.siteId(), request.buildingId()) : null;
        ZoneEntity zone = request.zoneId() != null ? resolveZone(organizationId, request.siteId(), request.zoneId()) : null;

        MeterEntity meter = new MeterEntity(organization, site, request.code(), request.name(), request.readingIntervalSeconds());
        meter.setMeterType(request.meterType());
        meter.setSerialNumber(request.serialNumber());
        meter.setManufacturer(request.manufacturer());
        meter.setModel(request.model());
        meter.setInstallationDate(request.installationDate());
        meter.setUnit(request.unit());
        meter.setBuilding(building);
        meter.setZone(zone);
        meter.setMetadata(metadataToString(request.metadata()));
        return toResponse(meterRepository.save(meter));
    }

    @Transactional(readOnly = true)
    public MeterResponse get(UUID organizationId, UUID meterId) {
        return toResponse(findMeter(organizationId, meterId));
    }

    @Transactional(readOnly = true)
    public PageResponse<MeterResponse> list(UUID organizationId, UUID siteId, UUID buildingId, UUID zoneId,
                                            MeterStatus status, String search, Integer page, Integer size, String[] sort) {
        Pageable pageable = PageableFactory.create(page, size, sort, ALLOWED_SORT_FIELDS);
        Specification<MeterEntity> spec = MeterSpecification.byOrganization(organizationId);
        if (siteId != null) {
            spec = spec.and(MeterSpecification.bySite(siteId));
        }
        if (buildingId != null) {
            spec = spec.and(MeterSpecification.byBuilding(buildingId));
        }
        if (zoneId != null) {
            spec = spec.and(MeterSpecification.byZone(zoneId));
        }
        if (status != null) {
            spec = spec.and(MeterSpecification.byStatus(status));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(MeterSpecification.search(search.trim()));
        }
        Page<MeterEntity> result = meterRepository.findAll(spec, pageable);
        return new PageResponse<>(result.map(this::toResponse).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public MeterResponse update(UUID organizationId, UUID meterId, UpdateMeterRequest request) {
        MeterEntity meter = findMeter(organizationId, meterId);
        if (request.name() != null) meter.setMeterName(request.name());
        if (request.serialNumber() != null) meter.setSerialNumber(request.serialNumber());
        if (request.manufacturer() != null) meter.setManufacturer(request.manufacturer());
        if (request.model() != null) meter.setModel(request.model());
        if (request.meterType() != null) meter.setMeterType(request.meterType());
        if (request.installationDate() != null) meter.setInstallationDate(request.installationDate());
        if (request.readingIntervalSeconds() != null) meter.setReadingIntervalSeconds(request.readingIntervalSeconds());
        if (request.unit() != null) meter.setUnit(request.unit());
        if (request.metadata() != null) meter.setMetadata(metadataToString(request.metadata()));
        return toResponse(meterRepository.save(meter));
    }

    @Transactional
    public MeterResponse activate(UUID organizationId, UUID meterId) {
        MeterEntity meter = findMeter(organizationId, meterId);
        ensureNotDecommissioned(meter, "activate");
        meter.setStatus(MeterStatus.ACTIVE);
        return toResponse(meterRepository.save(meter));
    }

    @Transactional
    public MeterResponse deactivate(UUID organizationId, UUID meterId) {
        MeterEntity meter = findMeter(organizationId, meterId);
        ensureNotDecommissioned(meter, "deactivate");
        meter.setStatus(MeterStatus.OFFLINE);
        return toResponse(meterRepository.save(meter));
    }

    @Transactional
    public MeterResponse commission(UUID organizationId, UUID meterId) {
        MeterEntity meter = findMeter(organizationId, meterId);
        if (meter.getStatus() == MeterStatus.DECOMMISSIONED) {
            throw new IllegalArgumentException("Cannot commission a decommissioned meter");
        }
        meter.setStatus(MeterStatus.ACTIVE);
        if (meter.getCommissionedAt() == null) {
            meter.setCommissionedAt(Instant.now());
        }
        return toResponse(meterRepository.save(meter));
    }

    @Transactional
    public MeterResponse decommission(UUID organizationId, UUID meterId) {
        MeterEntity meter = findMeter(organizationId, meterId);
        meter.setStatus(MeterStatus.DECOMMISSIONED);
        meter.setDecommissionedAt(Instant.now());
        return toResponse(meterRepository.save(meter));
    }

    @Transactional
    public MeterResponse assignLocation(UUID organizationId, UUID meterId, AssignLocationRequest request) {
        MeterEntity meter = findMeter(organizationId, meterId);
        ensureNotDecommissioned(meter, "change location");

        SiteEntity site = resolveSite(organizationId, request.siteId());
        BuildingEntity building = request.buildingId() != null ? resolveBuilding(organizationId, request.siteId(), request.buildingId()) : null;
        ZoneEntity zone = request.zoneId() != null ? resolveZone(organizationId, request.siteId(), request.zoneId()) : null;

        meter.setSite(site);
        meter.setBuilding(building);
        meter.setZone(zone);
        return toResponse(meterRepository.save(meter));
    }

    @Transactional
    public MeterResponse heartbeat(UUID organizationId, UUID meterId) {
        MeterEntity meter = findMeter(organizationId, meterId);
        meter.setLastSeenAt(Instant.now());
        return toResponse(meterRepository.save(meter));
    }

    private MeterEntity findMeter(UUID organizationId, UUID meterId) {
        return meterRepository.findByIdAndOrganizationId(meterId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Meter not found"));
    }

    private SiteEntity resolveSite(UUID organizationId, UUID siteId) {
        return siteRepository.findByIdAndOrganizationIdAndActiveTrue(siteId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Site does not belong to the organization"));
    }

    private BuildingEntity resolveBuilding(UUID organizationId, UUID siteId, UUID buildingId) {
        return buildingRepository.findByIdAndOrganizationIdAndActiveTrue(buildingId, organizationId)
                .filter(b -> b.getSite().getId().equals(siteId))
                .orElseThrow(() -> new IllegalArgumentException("Building does not belong to the specified site"));
    }

    private ZoneEntity resolveZone(UUID organizationId, UUID siteId, UUID zoneId) {
        return zoneRepository.findByIdAndOrganizationIdAndActiveTrue(zoneId, organizationId)
                .filter(z -> z.getSite().getId().equals(siteId))
                .orElseThrow(() -> new IllegalArgumentException("Zone does not belong to the specified site"));
    }

    private void ensureNotDecommissioned(MeterEntity meter, String action) {
        if (meter.getStatus() == MeterStatus.DECOMMISSIONED) {
            throw new IllegalArgumentException("Cannot " + action + " a decommissioned meter");
        }
    }

    private String metadataToString(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid metadata JSON: " + e.getMessage());
        }
    }

    private Map<String, String> metadataToMap(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored metadata", e);
        }
    }

    public MeterResponse toResponse(MeterEntity meter) {
        return new MeterResponse(
                meter.getId(),
                meter.getOrganization().getId(),
                meter.getSite().getId(),
                meter.getBuilding() != null ? meter.getBuilding().getId() : null,
                meter.getZone() != null ? meter.getZone().getId() : null,
                meter.getMeterCode(),
                meter.getMeterName(),
                meter.getSerialNumber(),
                meter.getManufacturer(),
                meter.getModel(),
                meter.getMeterType(),
                meter.getInstallationDate(),
                meter.getStatus(),
                meter.getReadingIntervalSeconds(),
                meter.getUnit(),
                meter.getLastSeenAt(),
                meter.getCommissionedAt(),
                meter.getDecommissionedAt(),
                metadataToMap(meter.getMetadata()),
                meter.getCreatedAt(),
                meter.getUpdatedAt(),
                meter.getVersion()
        );
    }
}
