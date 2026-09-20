package com.enerlytics.meter;

import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.facility.infrastructure.persistence.ZoneRepository;
import com.enerlytics.meter.api.dto.CreateMeterRequest;
import com.enerlytics.meter.api.dto.MeterResponse;
import com.enerlytics.meter.application.MeterService;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.domain.MeterType;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeterServiceTest {

    @Mock
    private MeterRepository meterRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MeterService meterService;

    @Test
    void createMeterWithValidRequestReturnsResponse() {
        UUID orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity("test", "Test");
        SiteEntity site = new SiteEntity(org, "S1", "Site", "UTC");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(siteRepository.findByIdAndOrganizationIdAndActiveTrue(any(), eq(orgId))).thenReturn(Optional.of(site));
        when(meterRepository.existsByOrganizationIdAndMeterCodeIgnoreCase(orgId, "M-001")).thenReturn(false);
        when(meterRepository.save(any(MeterEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMeterRequest request = new CreateMeterRequest(
                "M-001", "Main", "SN-1", "Acme", "X1", MeterType.ELECTRICITY,
                null, 60, "kWh", site.getId(), null, null, null);

        MeterResponse response = meterService.create(orgId, request);

        assertThat(response.code()).isEqualTo("M-001");
        assertThat(response.status()).isEqualTo(MeterStatus.PROVISIONING);
    }

    @Test
    void createMeterWithDuplicateCodeThrows() {
        UUID orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity("test", "Test");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(meterRepository.existsByOrganizationIdAndMeterCodeIgnoreCase(orgId, "M-001")).thenReturn(true);

        CreateMeterRequest request = new CreateMeterRequest(
                "M-001", "Main", null, null, null, MeterType.ELECTRICITY,
                null, 60, "kWh", UUID.randomUUID(), null, null, null);

        assertThrows(IllegalArgumentException.class, () -> meterService.create(orgId, request));
    }

    @Test
    void activateProvisioningMeterSetsStatusActive() {
        UUID orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity("test", "Test");
        SiteEntity site = new SiteEntity(org, "S1", "Site", "UTC");
        MeterEntity meter = new MeterEntity(org, site, "M-001", "Main", 60);
        when(meterRepository.findByIdAndOrganizationId(meter.getId(), orgId)).thenReturn(Optional.of(meter));
        when(meterRepository.save(meter)).thenReturn(meter);

        MeterResponse response = meterService.activate(orgId, meter.getId());

        assertThat(response.status()).isEqualTo(MeterStatus.ACTIVE);
    }
}
