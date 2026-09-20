package com.enerlytics.facility;

import com.enerlytics.facility.api.dto.CreateSiteRequest;
import com.enerlytics.facility.api.dto.SiteResponse;
import com.enerlytics.facility.application.SiteService;
import com.enerlytics.facility.domain.FloorAreaUnit;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private SiteService siteService;

    @Test
    void createSiteWithValidRequestReturnsResponse() {
        UUID orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity("test-org", "Test Org");
        // id is assigned by persistence; mocked repository returns org for the given orgId
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(siteRepository.existsByOrganizationIdAndSiteCodeIgnoreCase(orgId, "HQ")).thenReturn(false);
        when(siteRepository.save(any(SiteEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateSiteRequest request = new CreateSiteRequest(
                "HQ", "Headquarters", null, null, "US", null, null, null,
                null, null, "America/New_York", null, "USD",
                new BigDecimal("10000"), FloorAreaUnit.M2, LocalDate.now(), null);

        SiteResponse response = siteService.create(orgId, request);

        assertThat(response.code()).isEqualTo("HQ");
        assertThat(response.name()).isEqualTo("Headquarters");
        assertThat(response.timezone()).isEqualTo("America/New_York");
        verify(siteRepository).save(any(SiteEntity.class));
    }

    @Test
    void createSiteWithDuplicateCodeThrows() {
        UUID orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity("test-org", "Test Org");
        // id is assigned by persistence; mocked repository returns org for the given orgId
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(siteRepository.existsByOrganizationIdAndSiteCodeIgnoreCase(orgId, "HQ")).thenReturn(true);

        CreateSiteRequest request = new CreateSiteRequest(
                "HQ", "Headquarters", null, null, null, null, null, null,
                null, null, "UTC", null, "USD",
                null, FloorAreaUnit.M2, null, null);

        assertThrows(IllegalArgumentException.class, () -> siteService.create(orgId, request));
        verify(siteRepository, never()).save(any());
    }

    @Test
    void createSiteWithInvalidTimezoneThrows() {
        UUID orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity("test-org", "Test Org");
        // id is assigned by persistence; mocked repository returns org for the given orgId
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(siteRepository.existsByOrganizationIdAndSiteCodeIgnoreCase(orgId, "HQ")).thenReturn(false);

        CreateSiteRequest request = new CreateSiteRequest(
                "HQ", "Headquarters", null, null, null, null, null, null,
                null, null, "Mars/Colony", null, "USD",
                null, FloorAreaUnit.M2, null, null);

        assertThrows(IllegalArgumentException.class, () -> siteService.create(orgId, request));
    }
}
