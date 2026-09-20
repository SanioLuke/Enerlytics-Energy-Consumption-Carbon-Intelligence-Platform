package com.enerlytics.meter;

import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.domain.MeterType;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeterRepositoryTest {

    @Autowired
    private MeterRepository meterRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void findByIdAndOrganization_IdReturnsOwnedMeter() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("meter-org", "Meter Org"));
        SiteEntity site = siteRepository.save(new SiteEntity(org, "S-1", "Site One", "UTC"));
        MeterEntity meter = meterRepository.save(new MeterEntity(org, site, "M-001", "Main Meter", 60));

        assertThat(meterRepository.findByIdAndOrganization_Id(meter.getId(), org.getId())).isPresent();
        assertThat(meterRepository.findByIdAndOrganization_Id(meter.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void existsByOrganizationIdAndMeterCodeIsCaseInsensitive() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("meter-org2", "Meter Org 2"));
        SiteEntity site = siteRepository.save(new SiteEntity(org, "S-2", "Site Two", "UTC"));
        meterRepository.save(new MeterEntity(org, site, "M-ABC", "Meter ABC", 60));

        assertThat(meterRepository.existsByOrganization_IdAndMeterCodeIgnoreCase(org.getId(), "m-abc")).isTrue();
        assertThat(meterRepository.existsByOrganization_IdAndMeterCodeIgnoreCase(org.getId(), "M-ABC")).isTrue();
    }

    @Test
    void listByStatus() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("meter-org3", "Meter Org 3"));
        SiteEntity site = siteRepository.save(new SiteEntity(org, "S-3", "Site Three", "UTC"));
        MeterEntity active = new MeterEntity(org, site, "M-ACTIVE", "Active", 60);
        active.setStatus(MeterStatus.ACTIVE);
        MeterEntity offline = new MeterEntity(org, site, "M-OFFLINE", "Offline", 60);
        offline.setStatus(MeterStatus.OFFLINE);
        meterRepository.save(active);
        meterRepository.save(offline);

        Page<MeterEntity> page = meterRepository.findByOrganization_IdAndStatus(
                org.getId(), MeterStatus.ACTIVE, PageRequest.of(0, 10, Sort.by("meterName")));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(MeterStatus.ACTIVE);
    }
}
