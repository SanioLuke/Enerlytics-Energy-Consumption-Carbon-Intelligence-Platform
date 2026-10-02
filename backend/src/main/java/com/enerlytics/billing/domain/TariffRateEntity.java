package com.enerlytics.billing.domain;

import com.enerlytics.common.domain.AuditedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(schema = "billing", name = "tariff_rate")
public class TariffRateEntity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tariff_id", nullable = false)
    private TariffEntity tariff;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 16)
    private TariffDayType dayType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "cost_per_kwh", nullable = false, precision = 20, scale = 9)
    private BigDecimal costPerKwh;

    @Column(name = "demand_rate_per_kw", precision = 20, scale = 9)
    private BigDecimal demandRatePerKw;

    @Column(name = "priority", nullable = false)
    private int priority;

    protected TariffRateEntity() {
    }

    public TariffRateEntity(TariffDayType dayType, LocalTime startTime, LocalTime endTime,
                            BigDecimal costPerKwh, BigDecimal demandRatePerKw, int priority) {
        this.dayType = dayType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.costPerKwh = costPerKwh;
        this.demandRatePerKw = demandRatePerKw;
        this.priority = priority;
    }

    /**
     * Whether the window contains the given local time. Windows where
     * {@code startTime > endTime} wrap midnight (e.g. 22:00-06:00).
     */
    public boolean contains(LocalTime time) {
        if (startTime.isBefore(endTime)) {
            return !time.isBefore(startTime) && time.isBefore(endTime);
        }
        return !time.isBefore(startTime) || time.isBefore(endTime);
    }

    void setTariff(TariffEntity tariff) {
        this.tariff = tariff;
    }

    public TariffEntity getTariff() { return tariff; }
    public TariffDayType getDayType() { return dayType; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public BigDecimal getCostPerKwh() { return costPerKwh; }
    public BigDecimal getDemandRatePerKw() { return demandRatePerKw; }
    public int getPriority() { return priority; }
}
