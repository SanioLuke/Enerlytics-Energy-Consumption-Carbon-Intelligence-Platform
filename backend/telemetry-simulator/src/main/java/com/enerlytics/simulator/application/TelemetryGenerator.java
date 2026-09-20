package com.enerlytics.simulator.application;

import com.enerlytics.simulator.domain.SimulatedMeter;
import com.enerlytics.simulator.domain.TelemetryEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TelemetryGenerator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final LoadProfileCalculator loadProfileCalculator;
    private final ConcurrentMap<UUID, Random> meterRandoms = new ConcurrentHashMap<>();

    public TelemetryGenerator(LoadProfileCalculator loadProfileCalculator) {
        this.loadProfileCalculator = loadProfileCalculator;
    }

    public TelemetryEvent generate(SimulatedMeter meter, Instant timestamp, int sequence, Long globalSeed, double anomalyProbability) {
        Random random = meterRandoms.computeIfAbsent(meter.meterId(), id -> createMeterRandom(id, globalSeed));

        ZonedDateTime localTime = ZonedDateTime.ofInstant(timestamp, ZoneId.of(meter.timezone()));
        boolean anomaly = random.nextDouble() < anomalyProbability;

        BigDecimal powerKw = anomaly && random.nextBoolean()
                ? ZERO
                : loadProfileCalculator.computePowerKw(meter.profile(), localTime.toLocalDateTime(), localTime.getDayOfWeek(), random);

        BigDecimal voltage = loadProfileCalculator.computeVoltage(random, anomaly);
        BigDecimal powerFactor = loadProfileCalculator.computePowerFactor(random, anomaly);
        BigDecimal frequency = loadProfileCalculator.computeFrequency(random);
        BigDecimal current = loadProfileCalculator.computeCurrent(powerKw, voltage, powerFactor);

        double intervalHours = meter.readingIntervalSeconds() / 3600.0;
        BigDecimal energyKwh = powerKw.multiply(BigDecimal.valueOf(intervalHours))
                .setScale(9, RoundingMode.HALF_EVEN);

        TelemetryEvent event = new TelemetryEvent();
        event.setEventId(deterministicEventId(meter.meterId(), timestamp, sequence));
        event.setSchemaVersion("1.0");
        event.setMeterId(meter.meterId());
        event.setOrganizationId(meter.organizationId());
        event.setSiteId(meter.siteId());
        event.setTimestamp(timestamp);
        event.setEnergyKwh(energyKwh);
        event.setPowerKw(powerKw);
        event.setVoltage(voltage);
        event.setCurrent(current);
        event.setPowerFactor(powerFactor);
        event.setFrequency(frequency);
        event.setQualityStatus(anomaly ? resolveAnomalyStatus(random) : "OK");
        return event;
    }

    private Random createMeterRandom(UUID meterId, Long globalSeed) {
        long seed = globalSeed != null ? globalSeed : meterId.getMostSignificantBits() ^ meterId.getLeastSignificantBits();
        return new Random(seed);
    }

    private String resolveAnomalyStatus(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> "VOLTAGE_DIP";
            case 1 -> "POWER_FACTOR_LOW";
            case 2 -> "HARMONIC_DISTORTION";
            default -> "MISSING";
        };
    }

    private UUID deterministicEventId(UUID meterId, Instant timestamp, int sequence) {
        String payload = meterId.toString() + "|" + timestamp.toEpochMilli() + "|" + sequence;
        return UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
    }
}
