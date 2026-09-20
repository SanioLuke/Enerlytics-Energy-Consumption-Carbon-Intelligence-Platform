package com.enerlytics.meter.application;

import com.enerlytics.meter.api.dto.SimulatedMeterResponse;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SimulationService {

    private final MeterRepository meterRepository;

    public SimulationService(MeterRepository meterRepository) {
        this.meterRepository = meterRepository;
    }

    public List<SimulatedMeterResponse> findActiveSimulatedMeters() {
        return meterRepository.findBySimulatedTrueAndStatus(MeterStatus.ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    private SimulatedMeterResponse toResponse(MeterEntity meter) {
        return new SimulatedMeterResponse(
                meter.getId(),
                meter.getOrganization() != null ? meter.getOrganization().getId() : null,
                meter.getSite() != null ? meter.getSite().getId() : null,
                meter.getBuilding() != null ? meter.getBuilding().getId() : null,
                meter.getZone() != null ? meter.getZone().getId() : null,
                meter.getReadingIntervalSeconds(),
                meter.getSimulationProfile() != null ? meter.getSimulationProfile().name() : null,
                meter.getSite() != null ? meter.getSite().getIanaTimezone() : "UTC"
        );
    }

}
