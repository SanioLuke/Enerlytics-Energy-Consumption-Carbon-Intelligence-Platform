package com.enerlytics.organization.domain;

import com.enerlytics.common.domain.AuditedEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(schema = "org", name = "organization")
public class OrganizationEntity extends AuditedEntity {

    @Column(name = "organization_key", nullable = false, unique = true, length = 64)
    private String organizationKey;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "USD";

    @Column(name = "fiscal_year_start_month", nullable = false)
    private Integer fiscalYearStartMonth = 1;

    @Column(name = "locale", nullable = false, length = 35)
    private String locale = "en-US";

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected OrganizationEntity() {
    }

    public OrganizationEntity(String organizationKey, String displayName) {
        this.organizationKey = organizationKey;
        this.displayName = displayName;
    }

    public String getOrganizationKey() {
        return organizationKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public Integer getFiscalYearStartMonth() {
        return fiscalYearStartMonth;
    }

    public String getLocale() {
        return locale;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setStatus(OrganizationStatus status) {
        this.status = status;
    }
}
