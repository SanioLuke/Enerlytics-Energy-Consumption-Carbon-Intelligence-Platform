package com.enerlytics.billing.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.billing.api.dto.BaselineCostResponse;
import com.enerlytics.billing.api.dto.CostBucketResponse;
import com.enerlytics.billing.domain.EnergyCostEntity;
import com.enerlytics.billing.infrastructure.persistence.EnergyCostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class CostAnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final CostCalculationService calculationService;
    private final EnergyCostRepository costRepository;

    public CostAnalyticsService(CostCalculationService calculationService,
                                EnergyCostRepository costRepository) {
        this.calculationService = calculationService;
        this.costRepository = costRepository;
    }

    @Transactional
    public List<CostBucketResponse> costs(UUID orgId, DimensionType dimension, UUID dimensionId,
                                          AggregationGranularity granularity, Instant from, Instant to) {
        return map(calculationService.calculateCosts(orgId, dimension, dimensionId, granularity, from, to));
    }

    @Transactional
    public List<CostBucketResponse> dailyCost(UUID orgId, DimensionType dimension, UUID dimensionId,
                                              LocalDate date) {
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return map(calculationService.calculateCosts(orgId, dimension, dimensionId,
                AggregationGranularity.DAY, from, from.plusSeconds(86400)));
    }

    @Transactional
    public List<CostBucketResponse> monthlyCost(UUID orgId, DimensionType dimension, UUID dimensionId,
                                                YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return map(calculationService.calculateCosts(orgId, dimension, dimensionId,
                AggregationGranularity.MONTH, from, to));
    }

    @Transactional
    public List<CostBucketResponse> trend(UUID orgId, DimensionType dimension, UUID dimensionId,
                                          AggregationGranularity granularity, Instant from, Instant to) {
        return costs(orgId, dimension, dimensionId, granularity, from, to);
    }

    @Transactional
    public BaselineCostResponse baselineComparison(UUID orgId, DimensionType dimension, UUID dimensionId,
                                                   Instant currentFrom, Instant currentTo,
                                                   Instant baselineFrom, Instant baselineTo) {
        List<EnergyCostEntity> current = calculationService.calculateCosts(
                orgId, dimension, dimensionId, AggregationGranularity.DAY, currentFrom, currentTo);
        List<EnergyCostEntity> baseline = calculationService.calculateCosts(
                orgId, dimension, dimensionId, AggregationGranularity.DAY, baselineFrom, baselineTo);

        String currency = uniformCurrency(current);
        String baselineCurrency = uniformCurrency(baseline);
        if (currency != null && baselineCurrency != null && !currency.equals(baselineCurrency)) {
            throw new IllegalArgumentException("Current and baseline periods use different currencies");
        }
        if (currency == null) {
            currency = baselineCurrency;
        }

        BigDecimal currentTotal = sum(current);
        BigDecimal baselineTotal = sum(baseline);
        BigDecimal delta = baselineTotal == null || currentTotal == null
                ? null : currentTotal.subtract(baselineTotal);
        BigDecimal deltaPct = null;
        if (baselineTotal != null && baselineTotal.compareTo(ZERO) != 0 && delta != null) {
            deltaPct = delta.multiply(BigDecimal.valueOf(100))
                    .divide(baselineTotal, 4, RoundingMode.HALF_EVEN);
        }
        return new BaselineCostResponse(currency, currentFrom, currentTo, baselineFrom, baselineTo,
                currentTotal, baselineTotal, delta, deltaPct);
    }

    private String uniformCurrency(List<EnergyCostEntity> rows) {
        return rows.stream()
                .map(EnergyCostEntity::getCurrency)
                .filter(c -> c != null)
                .distinct()
                .reduce((a, b) -> null)
                .orElse(null);
    }

    private BigDecimal sum(List<EnergyCostEntity> rows) {
        if (rows.stream().anyMatch(r -> r.getTotalCost() == null)) {
            return null;
        }
        return rows.stream().map(EnergyCostEntity::getTotalCost).reduce(ZERO, BigDecimal::add);
    }

    private List<CostBucketResponse> map(List<EnergyCostEntity> entities) {
        return entities.stream()
                .map(e -> new CostBucketResponse(
                        e.getOrganizationId(), e.getDimensionType(), e.getDimensionId(), e.getGranularity(),
                        e.getBucketStart(), e.getBucketEnd(), e.getCurrency(), e.getEnergyConsumedKwh(),
                        e.getEnergyCost(), e.getDemandCharge(), e.getTotalCost(), e.getCoverageRatio(),
                        e.getQualityStatus(), e.getMissingRateHours()))
                .toList();
    }
}
