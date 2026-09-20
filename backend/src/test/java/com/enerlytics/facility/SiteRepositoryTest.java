package com.enerlytics.facility;

import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SiteRepositoryTest {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void findByIdAndOrganizationIdAndActiveTrueReturnsMatchingSite() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("repo-org", "Repo Org"));
        SiteEntity site = siteRepository.save(new SiteEntity(org, "HQ", "Headquarters", "UTC"));

        assertThat(siteRepository.findByIdAndOrganizationIdAndActiveTrue(site.getId(), org.getId())).isPresent();
        assertThat(siteRepository.findByIdAndOrganizationIdAndActiveTrue(site.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void existsByOrganizationIdAndSiteCodeIsCaseInsensitive() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("repo-org2", "Repo Org 2"));
        siteRepository.save(new SiteEntity(org, "Hq-01", "Headquarters", "UTC"));

        assertThat(siteRepository.existsByOrganizationIdAndSiteCodeIgnoreCase(org.getId(), "hq-01")).isTrue();
        assertThat(siteRepository.existsByOrganizationIdAndSiteCodeIgnoreCase(org.getId(), "HQ-01")).isTrue();
    }

    @Test
    void searchByNameOrCode() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("repo-org3", "Repo Org 3"));
        siteRepository.save(new SiteEntity(org, "PLANT", "Main Plant", "America/New_York"));
        siteRepository.save(new SiteEntity(org, "OFFICE", "Regional Office", "America/Los_Angeles"));

        Page<SiteEntity> page = siteRepository.findByOrganizationIdAndActiveTrueAndNameContainingIgnoreCaseOrSiteCodeContainingIgnoreCase(
                org.getId(), "Plant", "Plant", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSiteCode()).isEqualTo("PLANT");
    }

    @Test
    void paginationAndSorting() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("repo-org4", "Repo Org 4"));
        siteRepository.save(new SiteEntity(org, "A", "Alpha Site", "UTC"));
        siteRepository.save(new SiteEntity(org, "B", "Beta Site", "UTC"));
        siteRepository.save(new SiteEntity(org, "C", "Charlie Site", "UTC"));

        Page<SiteEntity> page = siteRepository.findByOrganizationIdAndActiveTrue(org.getId(),
                PageRequest.of(0, 2, Sort.by("name").ascending()));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Alpha Site");
    }

    @Test
    void sitePreservesAuditAndVersionFields() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("repo-org5", "Repo Org 5"));
        SiteEntity site = new SiteEntity(org, "AUDIT", "Audit Site", "UTC");
        site.setFloorArea(new BigDecimal("1234.56"));
        SiteEntity saved = siteRepository.save(site);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
    }
}
