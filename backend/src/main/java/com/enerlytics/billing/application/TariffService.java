package com.enerlytics.billing.application;

import com.enerlytics.billing.api.dto.TariffRateRequest;
import com.enerlytics.billing.api.dto.TariffRateResponse;
import com.enerlytics.billing.api.dto.TariffResponse;
import com.enerlytics.billing.api.dto.UpsertTariffRequest;
import com.enerlytics.billing.domain.TariffEntity;
import com.enerlytics.billing.domain.TariffRateEntity;
import com.enerlytics.billing.infrastructure.persistence.TariffRepository;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class TariffService {

    private final TariffRepository tariffRepository;
    private final SiteRepository siteRepository;

    public TariffService(TariffRepository tariffRepository, SiteRepository siteRepository) {
        this.tariffRepository = tariffRepository;
        this.siteRepository = siteRepository;
    }

    @Transactional
    public TariffResponse create(UUID organizationId, UpsertTariffRequest request) {
        SiteEntity site = siteRepository.findByIdAndOrganizationIdAndActiveTrue(request.siteId(), organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));
        validateRequest(request);
        ensureNoOverlap(organizationId, site.getId(), null, request.effectiveFrom(), request.effectiveTo());

        TariffEntity tariff = new TariffEntity(
                site.getOrganization(), site, request.name(), request.tariffType(),
                request.currency() != null ? request.currency() : site.getCurrency(),
                request.timezone() != null ? request.timezone() : site.getIanaTimezone(),
                request.effectiveFrom(), request.effectiveTo());
        applyRates(tariff, request.rates());
        return toResponse(tariffRepository.save(tariff));
    }

    @Transactional
    public TariffResponse update(UUID organizationId, UUID tariffId, UpsertTariffRequest request) {
        TariffEntity tariff = tariffRepository.findByIdAndOrganization_Id(tariffId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Tariff not found"));
        validateRequest(request);
        ensureNoOverlap(organizationId, tariff.getSite().getId(), tariffId, request.effectiveFrom(), request.effectiveTo());

        tariff.setName(request.name());
        tariff.setTariffType(request.tariffType());
        if (request.currency() != null) {
            tariff.setCurrency(request.currency());
        }
        if (request.timezone() != null) {
            tariff.setTimezone(request.timezone());
        }
        tariff.setEffectiveFrom(request.effectiveFrom());
        tariff.setEffectiveTo(request.effectiveTo());
        tariff.clearRates();
        applyRates(tariff, request.rates());
        return toResponse(tariffRepository.save(tariff));
    }

    @Transactional(readOnly = true)
    public TariffResponse get(UUID organizationId, UUID tariffId) {
        return toResponse(tariffRepository.findByIdAndOrganization_Id(tariffId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Tariff not found")));
    }

    @Transactional(readOnly = true)
    public List<TariffResponse> listForSite(UUID organizationId, UUID siteId) {
        siteRepository.findByIdAndOrganizationIdAndActiveTrue(siteId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));
        return tariffRepository.findByOrganization_IdAndSite_IdAndActiveTrueOrderByEffectiveFromDesc(
                        organizationId, siteId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deactivate(UUID organizationId, UUID tariffId) {
        TariffEntity tariff = tariffRepository.findByIdAndOrganization_Id(tariffId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Tariff not found"));
        tariff.deactivate();
        tariffRepository.save(tariff);
    }

    private void validateRequest(UpsertTariffRequest request) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new IllegalArgumentException("effectiveTo must be on or after effectiveFrom");
        }
        if (request.timezone() != null) {
            try {
                ZoneId.of(request.timezone());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid IANA timezone: " + request.timezone());
            }
        }
        if (request.rates() == null || request.rates().isEmpty()) {
            throw new IllegalArgumentException("Tariff requires at least one rate");
        }
        for (TariffRateRequest rate : request.rates()) {
            if (rate.startTime().equals(rate.endTime())) {
                throw new IllegalArgumentException("Rate startTime and endTime must differ");
            }
        }
    }

    private void ensureNoOverlap(UUID organizationId, UUID siteId, UUID excludeTariffId,
                                 LocalDate from, LocalDate to) {
        List<TariffEntity> existing = tariffRepository
                .findByOrganization_IdAndSite_IdAndActiveTrueOrderByEffectiveFromDesc(organizationId, siteId);
        for (TariffEntity other : existing) {
            if (other.getId().equals(excludeTariffId)) {
                continue;
            }
            boolean overlaps = !from.isAfter(other.getEffectiveTo() == null ? LocalDate.MAX : other.getEffectiveTo())
                    && !(to != null && to.isBefore(other.getEffectiveFrom()));
            if (overlaps) {
                throw new IllegalArgumentException(
                        "Tariff effective range overlaps existing tariff '" + other.getName() + "'");
            }
        }
    }

    private void applyRates(TariffEntity tariff, List<TariffRateRequest> rates) {
        for (TariffRateRequest r : rates) {
            tariff.addRate(new TariffRateEntity(r.dayType(), r.startTime(), r.endTime(),
                    r.costPerKwh(), r.demandRatePerKw(), r.priority() != null ? r.priority() : 0));
        }
    }

    private TariffResponse toResponse(TariffEntity tariff) {
        List<TariffRateResponse> rates = tariff.getRates().stream()
                .map(r -> new TariffRateResponse(r.getId(), r.getDayType(), r.getStartTime(), r.getEndTime(),
                        r.getCostPerKwh(), r.getDemandRatePerKw(), r.getPriority()))
                .toList();
        return new TariffResponse(tariff.getId(), tariff.getOrganization().getId(), tariff.getSite().getId(),
                tariff.getName(), tariff.getTariffType(), tariff.getCurrency(), tariff.getTimezone(),
                tariff.getEffectiveFrom(), tariff.getEffectiveTo(), tariff.isActive(), rates,
                tariff.getCreatedAt(), tariff.getUpdatedAt(), tariff.getVersion());
    }
}
