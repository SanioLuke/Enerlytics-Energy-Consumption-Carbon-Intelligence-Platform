package com.enerlytics.forecast.application;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.config.JpaConfig;
import com.enerlytics.forecast.api.dto.ForecastRunResponse;
import com.enerlytics.forecast.api.dto.ForecastRunSummaryResponse;
import com.enerlytics.forecast.config.ForecastConfig;
import com.enerlytics.forecast.domain.ForecastHorizon;
import com.enerlytics.forecast.domain.ForecastMethod;
import com.enerlytics.forecast.infrastructure.persistence.ForecastPointRepository;
import com.enerlytics.forecast.infrastructure.persistence.ForecastRunRepository;
import com.enerlytics.forecast.provider.SameHourBaselineProvider;
import com.enerlytics.forecast.provider.SeasonalMovingAverageProvider;
import com.enerlytics.forecast.provider.TrendAdjustedProvider;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ForecastConfig.class, EnergyForecastService.class,
        SeasonalMovingAverageProvider.class, SameHourBaselineProvider.class,
        TrendAdjustedProvider.class, EnergyForecastServiceTest.FixedClock.class})
class EnergyForecastServiceTest {

    /** Forecasts are generated as-of 2026-02-04 06:30 UTC. */
    private static final Instant AS_OF = Instant.parse("2026-02-04T06:30:00Z");
    /** First forecast bucket for NEXT_24_HOURS = next hour boundary. */
    private static final Instant FIRST_HOUR = Instant.parse("2026-02-04T07:00:00Z");
    /** First forecast bucket for NEXT_7_DAYS = next UTC midnight. */
    private static final Instant FIRST_DAY = Instant.parse("2026-02-05T00:00:00Z");
    private static final Instant START = Instant.parse("2026-01-05T00:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(AS_OF, ZoneOffset.UTC);
        }
    }

    @Autowired
    private EnergyForecastService service;

    @Autowired
    private EnergyAggregateRepository energyRepository;

    @Autowired
    private ForecastRunRepository runRepository;

    @Autowired
    private ForecastPointRepository pointRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @AfterEach
    void cleanUp() {
        pointRepository.deleteAll();
        runRepository.deleteAll();
        energyRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void generatesAndPersists24HourForecast() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("fc-1", "Forecast Org"));
        UUID meterId = UUID.randomUUID();
        // Hourly history covering the 28-day lookback; hour-of-day h carries 10 + h kWh.
        for (int i = 0; i < 30 * 24; i++) {
            Instant bucket = START.plus(i, ChronoUnit.HOURS);
            insert(org.getId(), meterId, bucket, AggregationGranularity.HOUR,
                    new BigDecimal(10 + bucket.atOffset(ZoneOffset.UTC).getHour()));
        }

        ForecastRunResponse response = service.generate(org.getId(), DimensionType.METER, meterId,
                ForecastHorizon.NEXT_24_HOURS, ForecastMethod.SAME_HOUR_BASELINE);

        assertThat(response.generatedAt()).isEqualTo(AS_OF);
        assertThat(response.method()).isEqualTo(ForecastMethod.SAME_HOUR_BASELINE);
        assertThat(response.points()).hasSize(24);
        assertThat(response.points().get(0).timestamp()).isEqualTo(FIRST_HOUR);
        assertThat(runRepository.count()).isEqualTo(1);
        assertThat(pointRepository.count()).isEqualTo(24);
        // Each predicted hour equals its same-hour-of-day mean: 10 + hour.
        response.points().forEach(p -> {
            int hour = p.timestamp().atOffset(ZoneOffset.UTC).getHour();
            assertThat(p.predictedKwh()).isEqualByComparingTo(new BigDecimal(10 + hour));
            assertThat(p.lowerBoundKwh()).isLessThanOrEqualTo(p.predictedKwh());
            assertThat(p.upperBoundKwh()).isGreaterThanOrEqualTo(p.predictedKwh());
            assertThat(p.method()).isEqualTo(ForecastMethod.SAME_HOUR_BASELINE);
            assertThat(p.generatedAt()).isEqualTo(AS_OF);
            assertThat(p.explanation()).isNotBlank();
        });
        assertThat(service.runs(org.getId(), DimensionType.METER, meterId,
                ForecastHorizon.NEXT_24_HOURS))
                .extracting(ForecastRunSummaryResponse::runId)
                .containsExactly(response.runId());
    }

    @Test
    void evaluatesActualsWithExactMaeAndMape() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("fc-2", "Eval Org"));
        UUID meterId = UUID.randomUUID();
        for (int i = 0; i < 30 * 24; i++) {
            insert(org.getId(), meterId, START.plus(i, ChronoUnit.HOURS),
                    AggregationGranularity.HOUR, new BigDecimal("10"));
        }

        ForecastRunResponse generated = service.generate(org.getId(), DimensionType.METER, meterId,
                ForecastHorizon.NEXT_24_HOURS, ForecastMethod.SAME_HOUR_BASELINE);
        // Constant history -> every bucket predicts 10 kWh.
        assertThat(generated.points()).allSatisfy(p ->
                assertThat(p.predictedKwh()).isEqualByComparingTo("10"));

        // Actuals for the first two forecast hours: 12 and 8.
        insert(org.getId(), meterId, FIRST_HOUR, AggregationGranularity.HOUR, new BigDecimal("12"));
        insert(org.getId(), meterId, FIRST_HOUR.plus(1, ChronoUnit.HOURS),
                AggregationGranularity.HOUR, new BigDecimal("8"));

        ForecastRunResponse evaluated = service.evaluate(org.getId(), generated.runId());

        // MAE = (|12-10| + |8-10|) / 2 = 2 exactly.
        assertThat(evaluated.maeKwh()).isEqualByComparingTo("2.000000000");
        // MAPE = (|12-10|/12*100 + |8-10|/8*100) / 2 = (16.666667 + 25) / 2
        //      = 20.8333335 -> HALF_EVEN scale 6 = 20.833334.
        assertThat(evaluated.mapePct()).isEqualByComparingTo("20.833334");
        assertThat(evaluated.evaluatedPoints()).isEqualTo(2);
        assertThat(evaluated.points().get(0).actualKwh()).isEqualByComparingTo("12");
        assertThat(evaluated.points().get(0).absoluteError()).isEqualByComparingTo("2.000000000");
        assertThat(evaluated.points().get(0).pctError()).isEqualByComparingTo("16.666667");
        assertThat(evaluated.points().get(2).actualKwh()).isNull();
    }

    @Test
    void mapeSkipsZeroActuals() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("fc-3", "Zero Org"));
        UUID meterId = UUID.randomUUID();
        for (int i = 0; i < 30 * 24; i++) {
            insert(org.getId(), meterId, START.plus(i, ChronoUnit.HOURS),
                    AggregationGranularity.HOUR, new BigDecimal("10"));
        }

        ForecastRunResponse generated = service.generate(org.getId(), DimensionType.METER, meterId,
                ForecastHorizon.NEXT_24_HOURS, ForecastMethod.SAME_HOUR_BASELINE);

        // Actual 0 at the first forecast hour: contributes to MAE, excluded from MAPE.
        insert(org.getId(), meterId, FIRST_HOUR, AggregationGranularity.HOUR, BigDecimal.ZERO);

        ForecastRunResponse evaluated = service.evaluate(org.getId(), generated.runId());

        assertThat(evaluated.maeKwh()).isEqualByComparingTo("10.000000000");
        assertThat(evaluated.mapePct()).isNull();
        assertThat(evaluated.points().get(0).pctError()).isNull();
        assertThat(evaluated.evaluatedPoints()).isEqualTo(1);
    }

    @Test
    void insufficientHistoryIsRejected() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("fc-4", "Short Org"));
        UUID meterId = UUID.randomUUID();
        for (int i = 0; i < 24; i++) {
            insert(org.getId(), meterId, START.plus(i, ChronoUnit.HOURS),
                    AggregationGranularity.HOUR, new BigDecimal("10"));
        }

        assertThatThrownBy(() -> service.generate(org.getId(), DimensionType.METER, meterId,
                ForecastHorizon.NEXT_24_HOURS, ForecastMethod.SAME_HOUR_BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient history");
        assertThat(runRepository.count()).isZero();
    }

    @Test
    void generates7DayForecastFromDailyAggregates() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("fc-5", "Daily Org"));
        UUID meterId = UUID.randomUUID();
        // 20 DAY buckets; daily total = 100 + 10*dayOfWeek (Mon=110 ... Sun=170).
        for (int i = 0; i < 20; i++) {
            Instant day = START.plus(i, ChronoUnit.DAYS);
            int dow = day.atOffset(ZoneOffset.UTC).getDayOfWeek().getValue();
            insert(org.getId(), meterId, day, AggregationGranularity.DAY,
                    new BigDecimal(100 + dow * 10));
        }

        ForecastRunResponse response = service.generate(org.getId(), DimensionType.METER, meterId,
                ForecastHorizon.NEXT_7_DAYS, ForecastMethod.SEASONAL_MOVING_AVERAGE);

        assertThat(response.points()).hasSize(7);
        assertThat(response.points().get(0).timestamp()).isEqualTo(FIRST_DAY);
        // FIRST_DAY is Thursday 2026-02-05 -> day-of-week mean = 100 + 4*10 = 140.
        assertThat(response.points().get(0).predictedKwh()).isEqualByComparingTo("140");
        // Friday -> 150, Saturday -> 160.
        assertThat(response.points().get(1).predictedKwh()).isEqualByComparingTo("150");
        assertThat(response.points().get(2).predictedKwh()).isEqualByComparingTo("160");
    }

    private EnergyAggregateEntity insert(UUID orgId, UUID meterId, Instant bucket,
                                         AggregationGranularity granularity, BigDecimal kwh) {
        EnergyAggregateEntity aggregate = new EnergyAggregateEntity(orgId, DimensionType.METER,
                meterId, granularity, bucket, granularity.bucketEnd(bucket));
        aggregate.applyMetrics(kwh, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ONE, 1, 0, new BigDecimal("100"), Instant.now());
        return energyRepository.save(aggregate);
    }
}
