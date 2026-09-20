package com.enerlytics.simulator.application;

import com.enerlytics.simulator.domain.SimulationProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class LoadProfileCalculatorTest {

    private final LoadProfileCalculator calculator = new LoadProfileCalculator();
    private final Random random = new Random(12345L);

    @Test
    void officeBusinessHoursAreHigherThanNight() {
        LocalDateTime noon = LocalDateTime.of(2026, Month.JANUARY, 5, 12, 0);
        BigDecimal noonLoad = calculator.computePowerKw(SimulationProfile.OFFICE, noon, DayOfWeek.MONDAY, random);

        LocalDateTime midnight = LocalDateTime.of(2026, Month.JANUARY, 5, 2, 0);
        BigDecimal nightLoad = calculator.computePowerKw(SimulationProfile.OFFICE, midnight, DayOfWeek.MONDAY, random);

        assertThat(noonLoad).isGreaterThan(nightLoad);
        assertThat(noonLoad).isGreaterThan(BigDecimal.valueOf(40));
    }

    @Test
    void weekendsAreLowerThanWeekdaysForOffice() {
        LocalDateTime noon = LocalDateTime.of(2026, Month.JANUARY, 3, 12, 0);
        BigDecimal saturdayLoad = calculator.computePowerKw(SimulationProfile.OFFICE, noon, DayOfWeek.SATURDAY, random);

        LocalDateTime mondayNoon = LocalDateTime.of(2026, Month.JANUARY, 5, 12, 0);
        BigDecimal mondayLoad = calculator.computePowerKw(SimulationProfile.OFFICE, mondayNoon, DayOfWeek.MONDAY, random);

        assertThat(mondayLoad).isGreaterThan(saturdayLoad);
    }

    @Test
    void dataCenterLoadIsRelativelyStable() {
        LocalDateTime morning = LocalDateTime.of(2026, Month.JANUARY, 5, 3, 0);
        LocalDateTime evening = LocalDateTime.of(2026, Month.JANUARY, 5, 20, 0);

        BigDecimal morningLoad = calculator.computePowerKw(SimulationProfile.DATA_CENTER, morning, DayOfWeek.MONDAY, random);
        BigDecimal eveningLoad = calculator.computePowerKw(SimulationProfile.DATA_CENTER, evening, DayOfWeek.MONDAY, random);

        assertThat(morningLoad).isGreaterThan(BigDecimal.valueOf(190));
        assertThat(eveningLoad).isGreaterThan(BigDecimal.valueOf(190));
    }

    @Test
    void anomalyLowersVoltage() {
        BigDecimal normal = calculator.computeVoltage(random, false);
        BigDecimal dip = calculator.computeVoltage(random, true);
        assertThat(dip).isLessThan(normal);
    }

    @Test
    void currentIsProportionalToPower() {
        BigDecimal lowCurrent = calculator.computeCurrent(BigDecimal.valueOf(10), BigDecimal.valueOf(230), BigDecimal.valueOf(0.95));
        BigDecimal highCurrent = calculator.computeCurrent(BigDecimal.valueOf(100), BigDecimal.valueOf(230), BigDecimal.valueOf(0.95));
        assertThat(highCurrent).isGreaterThan(lowCurrent);
    }
}
