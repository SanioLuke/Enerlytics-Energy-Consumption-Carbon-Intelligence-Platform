package com.enerlytics.analytics.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Periodic self-healing pass over recent aggregates. Recomputes every bucket
 * that received readings within the lookback window, converging aggregates
 * after late events, consumer outages, or backfills.
 */
@Component
@Profile("!test")
public class EnergyReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(EnergyReconciliationJob.class);

    private final EnergyAggregationService aggregationService;
    private final Duration lookback;

    public EnergyReconciliationJob(EnergyAggregationService aggregationService,
                                   @Value("${enerlytics.aggregation.reconcile-lookback:PT24H}") Duration lookback) {
        this.aggregationService = aggregationService;
        this.lookback = lookback;
    }

    @Scheduled(fixedDelayString = "${enerlytics.aggregation.reconcile-interval:PT5M}",
            initialDelayString = "${enerlytics.aggregation.reconcile-initial-delay:PT1M}")
    public void reconcile() {
        try {
            aggregationService.reconcile(Instant.now().minus(lookback));
        } catch (Exception e) {
            log.error("Energy aggregation reconciliation failed: {}", e.getMessage(), e);
        }
    }
}
