package com.enerlytics.billing.domain;

import com.enerlytics.common.domain.AuditedEntity;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.organization.domain.OrganizationEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = "billing", name = "tariff")
public class TariffEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tariff_type", nullable = false, length = 16)
    private TariffType tariffType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "timezone", nullable = false, length = 100)
    private String timezone;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "tariff", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("priority DESC, startTime ASC")
    private List<TariffRateEntity> rates = new ArrayList<>();

    protected TariffEntity() {
    }

    public TariffEntity(OrganizationEntity organization, SiteEntity site, String name,
                        TariffType tariffType, String currency, String timezone,
                        LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.organization = organization;
        this.site = site;
        this.name = name;
        this.tariffType = tariffType;
        this.currency = currency;
        this.timezone = timezone;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public boolean isEffectiveOn(LocalDate date) {
        if (!date.isBefore(effectiveFrom)) {
            return effectiveTo == null || !date.isAfter(effectiveTo);
        }
        return false;
    }

    public void deactivate() {
        this.active = false;
    }

    public void addRate(TariffRateEntity rate) {
        rates.add(rate);
        rate.setTariff(this);
    }

    public void clearRates() {
        rates.forEach(r -> r.setTariff(null));
        rates.clear();
    }

    public void setName(String name) { this.name = name; }
    public void setTariffType(TariffType tariffType) { this.tariffType = tariffType; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public OrganizationEntity getOrganization() { return organization; }
    public SiteEntity getSite() { return site; }
    public String getName() { return name; }
    public TariffType getTariffType() { return tariffType; }
    public String getCurrency() { return currency; }
    public String getTimezone() { return timezone; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public boolean isActive() { return active; }
    public List<TariffRateEntity> getRates() { return rates; }
}
