package com.enerlytics.forecast.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.forecast.api.dto.ForecastPointResponse;
import com.enerlytics.forecast.api.dto.ForecastRunResponse;
import com.enerlytics.forecast.api.dto.ForecastRunSummaryResponse;
import com.enerlytics.forecast.config.ForecastProperties;
import com.enerlytics.forecast.domain.*;
import com.enerlytics.forecast.infrastructure.persistence.ForecastPointRepository;
import com.enerlytics.forecast.infrastructure.persistence.ForecastRunRepository;
import com.enerlytics.forecast.provider.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EnergyForecastService {

    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final Map<ForecastMethod, ForecastProvider> providers;
    private final ForecastProperties properties;
    private final EnergyAggregateRepository aggregateRepository;
    private final ForecastRunRepository runRepository;
    private final ForecastPointRepository pointRepository;
    private final Clock clock;

    public EnergyForecastService(List<ForecastProvider> providers,
                                 ForecastProperties properties,
                                 EnergyAggregateRepository aggregateRepository,
                                 ForecastRunRepository runRepository,
                                 ForecastPointRepository pointRepository,
                                 Clock clock) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(ForecastProvider::method, Function.identity(),
                        (a, b) -> a, () -> new EnumMap<>(ForecastMethod.class)));
        this.properties = properties;
        this.aggregateRepository = aggregateRepository;
        this.runRepository = runRepository;
        this.pointRepository = pointRepository;
        this.clock = clock;
    }

    /**
     * Generates and persists a forecast run. History is loaded from canonical
     * energy aggregates at the horizon's granularity; incomplete buckets are
     * excluded before the provider sees the series.
     */
    @Transactional
    public ForecastRunResponse generate(UUID organizationId, DimensionType dimensionType,
                                        UUID dimensionId, ForecastHorizon horizon,
                                        ForecastMethod method) {
        ForecastProvider provider = providers.get(method);
        if (provider == null) {
            throw new IllegalArgumentException("No forecast provider registered for method " + method);
        }

        Instant generatedAt = Instant.now(clock);
        Instant firstBucket = horizon.firstBucketStart(generatedAt);
        Instant historyFrom = firstBucket.minus(Duration.ofDays(properties.getHistoryLookbackDays()));

        List<ForecastSample> history = aggregateRepository
                .findByOrganizationIdAndDimensionTypeAndDimensionIdAndGranularityAndBucketStartBetweenOrderByBucketStart(
                        organizationId, dimensionType, dimensionId, horizon.getGranularity(),
                        historyFrom, firstBucket.minusNanos(1))
                .stream()
                .filter(this::hasUsableTelemetry)
                .map(e -> new ForecastSample(e.getBucketStart(), e.getEnergyConsumedKwh()))
                .toList();

        int required = switch (horizon) {
            case NEXT_24_HOURS -> properties.getMinHistorySamplesHour();
            case NEXT_7_DAYS -> properties.getMinHistorySamplesDay();
        };
        if (history.size() < required) {
            throw new IllegalArgumentException("Insufficient history: " + history.size()
                    + " " + horizon.getGranularity() + " buckets available, " + required + " required");
        }

        ForecastContext context = new ForecastContext(organizationId, dimensionType, dimensionId,
                horizon, firstBucket, history, properties);
        List<ForecastPrediction> predictions = provider.forecast(context);
        if (predictions.size() != horizon.getBucketCount()) {
            throw new IllegalStateException("Provider " + method + " returned " + predictions.size()
                    + " predictions, expected " + horizon.getBucketCount());
        }

        ForecastRunEntity run = runRepository.save(new ForecastRunEntity(
                organizationId, dimensionType, dimensionId, horizon, method,
                generatedAt, historyFrom, firstBucket));

        List<ForecastPointEntity> points = predictions.stream()
                .map(p -> new ForecastPointEntity(run.getId(), organizationId, p.timestamp(),
                        p.predictedKwh(), p.lowerBoundKwh(), p.upperBoundKwh(), p.explanation()))
                .toList();
        pointRepository.saveAll(points);

        return toRunResponse(run, points);
    }

    @Transactional(readOnly = true)
    public List<ForecastRunSummaryResponse> runs(UUID organizationId, DimensionType dimensionType,
                                               UUID dimensionId, ForecastHorizon horizon) {
        List<ForecastRunEntity> runs = horizon == null
                ? runRepository.findByOrganizationIdAndDimensionTypeAndDimensionIdOrderByGeneratedAtDesc(
                        organizationId, dimensionType, dimensionId)
                : runRepository.findByOrganizationIdAndDimensionTypeAndDimensionIdAndHorizonOrderByGeneratedAtDesc(
                        organizationId, dimensionType, dimensionId, horizon);
        return runs.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ForecastRunResponse run(UUID organizationId, UUID runId) {
        ForecastRunEntity run = runRepository.findByIdAndOrganizationId(runId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Forecast run not found"));
        return toRunResponse(run, pointRepository.findByRunIdOrderByBucketStart(runId));
    }

    /**
     * Fills actual consumption for forecast buckets whose canonical aggregates
     * now exist, then recomputes run-level MAE and MAPE. Buckets with actuals
     * at or below {@code mapeMinActualKwh} contribute to MAE but are excluded
     * from MAPE, where the percentage would be meaningless.
     */
    @Transactional
    public ForecastRunResponse evaluate(UUID organizationId, UUID runId) {
        ForecastRunEntity run = runRepository.findByIdAndOrganizationId(runId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Forecast run not found"));
        List<ForecastPointEntity> points = pointRepository.findByRunIdOrderByBucketStart(runId);

        for (ForecastPointEntity point : points) {
            aggregateRepository
                    .findByDimensionTypeAndDimensionIdAndGranularityAndBucketStart(
                            run.getDimensionType(), run.getDimensionId(),
                            run.getHorizon().getGranularity(), point.getBucketStart())
                    .map(EnergyAggregateEntity::getEnergyConsumedKwh)
                    .ifPresent(actual -> {
                        BigDecimal absError = actual.subtract(point.getPredictedKwh(), MC).abs();
                        BigDecimal pctError = actual.compareTo(properties.getMapeMinActualKwh()) > 0
                                ? absError.divide(actual, MC).multiply(HUNDRED, MC)
                                        .setScale(6, RoundingMode.HALF_EVEN)
                                : null;
                        point.applyActual(actual, absError.setScale(9, RoundingMode.HALF_EVEN), pctError);
                    });
        }

        List<BigDecimal> absErrors = new ArrayList<>();
        List<BigDecimal> pctErrors = new ArrayList<>();
        for (ForecastPointEntity p : points) {
            if (p.getAbsoluteError() != null) {
                absErrors.add(p.getAbsoluteError());
            }
            if (p.getPctError() != null) {
                pctErrors.add(p.getPctError());
            }
        }
        BigDecimal mae = absErrors.isEmpty() ? null
                : mean(absErrors).setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal mape = pctErrors.isEmpty() ? null
                : mean(pctErrors).setScale(6, RoundingMode.HALF_EVEN);
        run.applyEvaluation(mae, mape, absErrors.size(), Instant.now(clock));

        return toRunResponse(runRepository.save(run), points);
    }

    private boolean hasUsableTelemetry(EnergyAggregateEntity row) {
        return row.getEnergyConsumedKwh() != null
                && row.getDataCompletenessPct() != null
                && row.getDataCompletenessPct().compareTo(properties.getMinCompletenessPct()) >= 0;
    }

    private BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v, MC);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    private ForecastRunResponse toRunResponse(ForecastRunEntity run, List<ForecastPointEntity> points) {
        List<ForecastPointResponse> pointResponses = points.stream()
                .map(p -> new ForecastPointResponse(p.getBucketStart(), p.getPredictedKwh(),
                        p.getLowerBoundKwh(), p.getUpperBoundKwh(), run.getMethod(),
                        run.getGeneratedAt(), p.getActualKwh(), p.getAbsoluteError(),
                        p.getPctError(), p.getExplanation()))
                .toList();
        return new ForecastRunResponse(run.getId(), run.getDimensionType(), run.getDimensionId(),
                run.getHorizon(), run.getMethod(), run.getGeneratedAt(), run.getMaeKwh(),
                run.getMapePct(), run.getEvaluatedPoints(), pointResponses);
    }

    private ForecastRunSummaryResponse toSummary(ForecastRunEntity run) {
        return new ForecastRunSummaryResponse(run.getId(), run.getDimensionType(),
                run.getDimensionId(), run.getHorizon(), run.getMethod(), run.getGeneratedAt(),
                run.getMaeKwh(), run.getMapePct(), run.getEvaluatedPoints(), run.getEvaluatedAt());
    }
}
