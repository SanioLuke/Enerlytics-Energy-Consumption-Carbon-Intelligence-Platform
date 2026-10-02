package com.enerlytics.carbon.infrastructure.persistence;

import com.enerlytics.carbon.domain.CarbonIntensityObservationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CarbonIntensityObservationRepositoryTest {

    @Autowired
    private CarbonIntensityObservationRepository repository;

    @Test
    void persistsAndFindsLatestObservation() {
        repository.save(observation("ELECTRICITY_MAPS", "DE", "2026-01-15T10:00:00Z", 250, false));
        repository.save(observation("ELECTRICITY_MAPS", "DE", "2026-01-15T11:00:00Z", 300, true));

        Optional<CarbonIntensityObservationEntity> latest = repository.findFirstByProviderAndZoneOrderByObservedAtDesc("ELECTRICITY_MAPS", "DE");

        assertThat(latest).isPresent();
        assertThat(latest.get().getObservedAt()).isEqualTo(Instant.parse("2026-01-15T11:00:00Z"));
        assertThat(latest.get().getCarbonIntensityGCo2EqPerKwh()).isEqualTo(300);
        assertThat(latest.get().isEstimated()).isTrue();
    }

    @Test
    void findsByProviderZoneAndObservedAtBetween() {
        repository.save(observation("ELECTRICITY_MAPS", "FR", "2026-01-15T08:00:00Z", 100, false));
        repository.save(observation("ELECTRICITY_MAPS", "FR", "2026-01-15T09:00:00Z", 200, false));
        repository.save(observation("ELECTRICITY_MAPS", "FR", "2026-01-15T10:00:00Z", 300, false));

        List<CarbonIntensityObservationEntity> results = repository.findByProviderAndZoneAndObservedAtBetween(
                "ELECTRICITY_MAPS", "FR",
                Instant.parse("2026-01-15T08:30:00Z"),
                Instant.parse("2026-01-15T10:00:00Z"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCarbonIntensityGCo2EqPerKwh()).isEqualTo(200);
    }

    private CarbonIntensityObservationEntity observation(String provider, String zone, String timestamp, int value, boolean estimated) {
        return new CarbonIntensityObservationEntity(
                provider, zone, Instant.parse(timestamp), value, estimated, Instant.now());
    }
}
