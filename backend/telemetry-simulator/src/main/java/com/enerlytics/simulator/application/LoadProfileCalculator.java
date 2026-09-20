package com.enerlytics.simulator.application;

import com.enerlytics.simulator.domain.SimulationProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Random;

@Component
public class LoadProfileCalculator {

    private static final BigDecimal SQRT_3 = BigDecimal.valueOf(Math.sqrt(3));

    public BigDecimal computePowerKw(SimulationProfile profile, LocalDateTime localTime, DayOfWeek dayOfWeek, Random random) {
        int hour = localTime.getHour();
        boolean weekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

        double baseLoad;
        double peakLoad;
        int peakStart;
        int peakEnd;
        double weekendFactor;
        double randomFactor;

        switch (profile) {
            case OFFICE -> {
                baseLoad = 15.0;
                peakLoad = 80.0;
                peakStart = 8;
                peakEnd = 18;
                weekendFactor = 0.35;
                randomFactor = 0.10;
            }
            case DATA_CENTER -> {
                baseLoad = 200.0;
                peakLoad = 220.0;
                peakStart = 0;
                peakEnd = 24;
                weekendFactor = 0.95;
                randomFactor = 0.05;
            }
            case WAREHOUSE -> {
                baseLoad = 20.0;
                peakLoad = 60.0;
                peakStart = 7;
                peakEnd = 17;
                weekendFactor = 0.30;
                randomFactor = 0.12;
            }
            case RETAIL -> {
                baseLoad = 25.0;
                peakLoad = 100.0;
                peakStart = 10;
                peakEnd = 20;
                weekendFactor = 1.1;
                randomFactor = 0.15;
            }
            case MANUFACTURING -> {
                baseLoad = 100.0;
                peakLoad = 200.0;
                peakStart = 6;
                peakEnd = 22;
                weekendFactor = 0.50;
                randomFactor = 0.08;
            }
            case RESIDENTIAL -> {
                baseLoad = 1.0;
                peakLoad = 4.0;
                peakStart = 18;
                peakEnd = 22;
                weekendFactor = 1.05;
                randomFactor = 0.20;
            }
            default -> throw new IllegalArgumentException("Unknown profile: " + profile);
        }

        double load = baseLoad;
        boolean inPeak = hour >= peakStart && hour < peakEnd;
        if (inPeak && !weekend) {
            load = peakLoad;
        } else if (inPeak) {
            load = baseLoad + (peakLoad - baseLoad) * weekendFactor;
        }

        if (weekend) {
            load *= weekendFactor;
        }

        double variation = 1.0 + (random.nextDouble() * 2 - 1) * randomFactor;
        load = Math.max(0.1, load * variation);

        return BigDecimal.valueOf(load).setScale(6, RoundingMode.HALF_EVEN);
    }

    public BigDecimal computeVoltage(Random random, boolean anomaly) {
        double nominal = 230.0;
        if (anomaly) {
            return BigDecimal.valueOf(nominal * (0.85 + random.nextDouble() * 0.10)).setScale(3, RoundingMode.HALF_EVEN);
        }
        return BigDecimal.valueOf(nominal + (random.nextDouble() * 4 - 2)).setScale(3, RoundingMode.HALF_EVEN);
    }

    public BigDecimal computeCurrent(BigDecimal powerKw, BigDecimal voltage, BigDecimal powerFactor) {
        if (voltage.compareTo(BigDecimal.ZERO) == 0 || powerFactor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // Three-phase approximation: I = P * 1000 / (V * PF * sqrt(3))
        BigDecimal denominator = voltage.multiply(powerFactor).multiply(SQRT_3);
        return powerKw.multiply(BigDecimal.valueOf(1000))
                .divide(denominator, 6, RoundingMode.HALF_EVEN);
    }

    public BigDecimal computePowerFactor(Random random, boolean anomaly) {
        if (anomaly) {
            return BigDecimal.valueOf(0.70 + random.nextDouble() * 0.15).setScale(4, RoundingMode.HALF_EVEN);
        }
        return BigDecimal.valueOf(0.92 + random.nextDouble() * 0.06).setScale(4, RoundingMode.HALF_EVEN);
    }

    public BigDecimal computeFrequency(Random random) {
        return BigDecimal.valueOf(50.0 + (random.nextDouble() * 0.2 - 0.1)).setScale(3, RoundingMode.HALF_EVEN);
    }
}
