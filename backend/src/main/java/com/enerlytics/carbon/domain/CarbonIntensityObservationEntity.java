package com.enerlytics.carbon.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "carbon", name = "intensity_observation",
        uniqueConstraints = @UniqueConstraint(name = "uq_intensity_observation",
                columnNames = {"provider", "zone", "observed_at"}))
public class CarbonIntensityObservationEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "zone", nullable = false, length = 32)
    private String zone;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "carbon_intensity_gco2eq_per_kwh", nullable = false)
    private Integer carbonIntensityGCo2EqPerKwh;

    @Column(name = "estimated", nullable = false)
    private boolean estimated;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    protected CarbonIntensityObservationEntity() {
    }

    public CarbonIntensityObservationEntity(String provider, String zone, Instant observedAt,
                                            int carbonIntensityGCo2EqPerKwh, boolean estimated,
                                            Instant retrievedAt) {
        this.provider = provider;
        this.zone = zone;
        this.observedAt = observedAt;
        this.carbonIntensityGCo2EqPerKwh = carbonIntensityGCo2EqPerKwh;
        this.estimated = estimated;
        this.retrievedAt = retrievedAt;
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getZone() { return zone; }
    public Instant getObservedAt() { return observedAt; }
    public Integer getCarbonIntensityGCo2EqPerKwh() { return carbonIntensityGCo2EqPerKwh; }
    public boolean isEstimated() { return estimated; }
    public Instant getRetrievedAt() { return retrievedAt; }
}
