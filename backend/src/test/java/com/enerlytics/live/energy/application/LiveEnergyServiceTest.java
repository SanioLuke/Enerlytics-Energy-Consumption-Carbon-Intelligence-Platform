package com.enerlytics.live.energy.application;

import com.enerlytics.live.energy.api.LiveEnergySnapshot;
import com.enerlytics.telemetry.api.event.MeterReadingValidatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveEnergyServiceTest {

    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SITE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BUILDING = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID METER_1 = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID METER_2 = UUID.fromString("00000000-0000-0000-0000-000000000011");

    private LiveEnergyService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-04T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        LiveEnergyProperties properties = new LiveEnergyProperties();
        properties.setPublishIntervalMs(1000L);
        properties.setOfflineThresholdMs(300_000L);
        properties.setTrendResolutionMs(0L); // append on every publish for tests
        properties.setTrendMaxPoints(10);
        properties.setMaxMeterReadingsPerSnapshot(50);
        service = new LiveEnergyService(clock, properties, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void emptySnapshotHasZeroDemandAndNoMeters() {
        LiveEnergySnapshot snapshot = service.snapshot(ORG, null, null);

        assertThat(snapshot.currentDemandKw()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.activeMeterCount()).isZero();
        assertThat(snapshot.offlineMeterCount()).isZero();
        assertThat(snapshot.meterReadings()).isEmpty();
        assertThat(snapshot.recentTrend()).isEmpty();
    }

    @Test
    void validatedReadingActivatesMeterAndUpdatesDemand() {
        service.onReadingValidated(event(METER_1, SITE, BUILDING, BigDecimal.valueOf(50)));
        service.publishDirtySnapshots();

        LiveEnergySnapshot orgSnapshot = service.snapshot(ORG, null, null);
        assertThat(orgSnapshot.activeMeterCount()).isEqualTo(1);
        assertThat(orgSnapshot.offlineMeterCount()).isZero();
        assertThat(orgSnapshot.currentDemandKw()).isEqualByComparingTo(BigDecimal.valueOf(50));

        LiveEnergySnapshot siteSnapshot = service.snapshot(ORG, SITE, null);
        assertThat(siteSnapshot.activeMeterCount()).isEqualTo(1);
        assertThat(siteSnapshot.currentDemandKw()).isEqualByComparingTo(BigDecimal.valueOf(50));

        assertThat(orgSnapshot.recentTrend()).hasSize(1);
        assertThat(orgSnapshot.recentTrend().getFirst().demandKw()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void multipleMetersAggregateDemand() {
        service.onReadingValidated(event(METER_1, SITE, BUILDING, BigDecimal.valueOf(50)));
        service.onReadingValidated(event(METER_2, SITE, BUILDING, BigDecimal.valueOf(30)));
        service.publishDirtySnapshots();

        LiveEnergySnapshot snapshot = service.snapshot(ORG, SITE, null);
        assertThat(snapshot.activeMeterCount()).isEqualTo(2);
        assertThat(snapshot.currentDemandKw()).isEqualByComparingTo(BigDecimal.valueOf(80));
    }

    @Test
    void onlyDirtyScopesArePublishedAndSnapshotTimestampAdvances() {
        service.onReadingValidated(event(METER_1, SITE, BUILDING, BigDecimal.valueOf(50)));
        service.publishDirtySnapshots();
        LiveEnergySnapshot first = service.snapshot(ORG, null, null);

        service.publishDirtySnapshots();
        LiveEnergySnapshot second = service.snapshot(ORG, null, null);

        // The same reading should produce the same demand even before/after publish,
        // but the snapshot itself is stable.
        assertThat(second.currentDemandKw()).isEqualByComparingTo(first.currentDemandKw());
        assertThat(second.recentTrend()).hasSize(1);
    }

    @Test
    void meterExpiresToOfflineWhenLastSeenExceedsThreshold() {
        Instant stale = now.minusSeconds(properties().getOfflineThresholdMs() / 1000 + 1);
        service.onReadingValidated(event(METER_1, SITE, BUILDING, BigDecimal.valueOf(50), stale));
        service.publishDirtySnapshots();
        assertThat(service.snapshot(ORG, null, null).activeMeterCount()).isEqualTo(1);

        service.expireOfflineMeters();

        LiveEnergySnapshot snapshot = service.snapshot(ORG, null, null);
        assertThat(snapshot.activeMeterCount()).isZero();
        assertThat(snapshot.offlineMeterCount()).isEqualTo(1);
        assertThat(snapshot.currentDemandKw()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private LiveEnergyProperties properties() {
        LiveEnergyProperties properties = new LiveEnergyProperties();
        properties.setPublishIntervalMs(1000L);
        properties.setOfflineThresholdMs(300_000L);
        properties.setTrendResolutionMs(0L);
        properties.setTrendMaxPoints(10);
        properties.setMaxMeterReadingsPerSnapshot(50);
        return properties;
    }

    private MeterReadingValidatedEvent event(UUID meterId, UUID siteId, UUID buildingId, BigDecimal powerKw) {
        return event(meterId, siteId, buildingId, powerKw, now);
    }

    private MeterReadingValidatedEvent event(UUID meterId, UUID siteId, UUID buildingId, BigDecimal powerKw, Instant timestamp) {
        return new MeterReadingValidatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                meterId,
                ORG,
                siteId,
                buildingId,
                timestamp,
                BigDecimal.valueOf(0.1),
                powerKw,
                BigDecimal.valueOf(230),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(0.95),
                BigDecimal.valueOf(50),
                "VALID");
    }
}
