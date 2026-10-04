package com.enerlytics.live.energy.application;

import com.enerlytics.live.energy.api.LiveEnergySnapshot;
import com.enerlytics.live.energy.api.MeterLiveReading;
import com.enerlytics.live.energy.api.TrendPoint;
import com.enerlytics.telemetry.api.event.MeterReadingValidatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory live energy monitoring state engine.
 *
 * <p>Validated meter readings update per-meter state and per-scope aggregate
 * demand. A scheduled task coalesces changes into SSE snapshots at a fixed
 * cadence so browsers receive at most one update per interval even if thousands
 * of readings arrive per second. A separate scheduled task marks meters offline
 * when no reading has been seen within the configured threshold.
 *
 * <p>All state is held in memory; losing the process loses the live view. That
 * is acceptable for a monitoring UI — the authoritative telemetry store
 * remains the database.
 */
@Service
public class LiveEnergyService {

    private static final Logger log = LoggerFactory.getLogger(LiveEnergyService.class);

    private final Clock clock;
    private final LiveEnergyProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<UUID, MeterState> meters = new ConcurrentHashMap<>();
    private final Map<ScopeKey, ScopeState> scopes = new ConcurrentHashMap<>();
    private final Map<ScopeKey, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public LiveEnergyService(Clock clock, LiveEnergyProperties properties, ObjectMapper objectMapper) {
        this.clock = clock;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Receive validated readings from the telemetry ingestion pipeline.
     * Updates happen without per-reading SSE emission; the scheduled publisher
     * batches them.
     */
    @EventListener
    public void onReadingValidated(MeterReadingValidatedEvent event) {
        if (event == null || event.meterId() == null || event.organizationId() == null) {
            return;
        }

        Instant now = clock.instant();
        MeterState meter = meters.computeIfAbsent(event.meterId(), id -> new MeterState());
        meter.meterId = event.meterId();
        meter.organizationId = event.organizationId();
        meter.siteId = event.siteId();
        meter.buildingId = event.buildingId();
        meter.lastSeenAt = event.timestamp();
        meter.powerKw = event.powerKw();
        meter.energyKwh = event.energyKwh();
        meter.online = true;

        ScopeKey org = ScopeKey.of(event.organizationId(), null, null);
        ScopeKey site = event.siteId() != null
                ? ScopeKey.of(event.organizationId(), event.siteId(), null)
                : null;
        ScopeKey building = event.buildingId() != null
                ? ScopeKey.of(event.organizationId(), event.siteId(), event.buildingId())
                : null;

        synchronized (scope(org)) {
            updateScopeForActiveMeter(scope(org), meter, now);
        }
        if (site != null) {
            synchronized (scope(site)) {
                updateScopeForActiveMeter(scope(site), meter, now);
            }
        }
        if (building != null) {
            synchronized (scope(building)) {
                updateScopeForActiveMeter(scope(building), meter, now);
            }
        }
    }

    /**
     * Coalesces all dirty scopes into SSE snapshots and pushes them to
     * subscribed clients. This is the throttling boundary: even under heavy
     * telemetry throughput, browsers receive at most one snapshot per
     * publish interval per scope.
     */
    @Scheduled(fixedRateString = "${enerlytics.live.energy.publish-interval-ms:1000}")
    public void publishDirtySnapshots() {
        Instant now = clock.instant();
        List<ScopeKey> dirty = scopes.values().stream()
                .filter(s -> s.dirty.get())
                .map(s -> s.key)
                .toList();

        for (ScopeKey key : dirty) {
            ScopeState state = scope(key);
            LiveEnergySnapshot snapshot;
            synchronized (state) {
                snapshot = buildSnapshot(state, key, now);
                state.dirty.set(false);
            }
            broadcast(key, snapshot);
        }
    }

    /**
     * Marks meters offline when their last reading is older than the threshold,
     * then dirties every affected scope so subscribers see the status change
     * on the next publish cycle.
     */
    @Scheduled(fixedRateString = "${enerlytics.live.energy.offline-check-interval-ms:60000}")
    public void expireOfflineMeters() {
        Instant now = clock.instant();
        long thresholdMs = properties.getOfflineThresholdMs();

        meters.values().stream()
                .filter(m -> m.online && m.lastSeenAt != null && now.toEpochMilli() - m.lastSeenAt.toEpochMilli() > thresholdMs)
                .forEach(this::markMeterOffline);
    }

    public SseEmitter subscribe(UUID organizationId, UUID siteId, UUID buildingId) {
        Objects.requireNonNull(organizationId, "organizationId is required");
        ScopeKey key = ScopeKey.of(organizationId, siteId, buildingId);
        SseEmitter emitter = new SseEmitter(properties.getEmitterTimeoutMs());
        Set<SseEmitter> set = emitters.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);

        emitter.onCompletion(() -> set.remove(emitter));
        emitter.onTimeout(() -> set.remove(emitter));
        emitter.onError(e -> set.remove(emitter));

        // Immediately emit the latest known snapshot (or an empty one) so the
        // UI does not sit blank until the next reading arrives.
        LiveEnergySnapshot snapshot;
        synchronized (scope(key)) {
            snapshot = buildSnapshot(scope(key), key, clock.instant());
        }
        send(emitter, snapshot);

        return emitter;
    }

    public LiveEnergySnapshot snapshot(UUID organizationId, UUID siteId, UUID buildingId) {
        ScopeKey key = ScopeKey.of(organizationId, siteId, buildingId);
        synchronized (scope(key)) {
            return buildSnapshot(scope(key), key, clock.instant());
        }
    }

    int meterCount() {
        return meters.size();
    }

    int subscriberCount(ScopeKey key) {
        return emitters.getOrDefault(key, Set.of()).size();
    }

    private void markMeterOffline(MeterState meter) {
        meter.online = false;
        Instant now = clock.instant();
        ScopeKey org = ScopeKey.of(meter.organizationId, null, null);
        ScopeKey site = meter.siteId != null ? ScopeKey.of(meter.organizationId, meter.siteId, null) : null;
        ScopeKey building = meter.buildingId != null
                ? ScopeKey.of(meter.organizationId, meter.siteId, meter.buildingId)
                : null;

        synchronized (scope(org)) {
            moveToOffline(scope(org), meter, now);
        }
        if (site != null) {
            synchronized (scope(site)) {
                moveToOffline(scope(site), meter, now);
            }
        }
        if (building != null) {
            synchronized (scope(building)) {
                moveToOffline(scope(building), meter, now);
            }
        }
    }

    private void updateScopeForActiveMeter(ScopeState state, MeterState meter, Instant now) {
        boolean added = state.active.add(meter.meterId);
        boolean removed = state.offline.remove(meter.meterId);
        if (added || removed) {
            recalcDemand(state);
        } else {
            // Power may have changed; recompute the existing aggregate.
            state.demand = sumActivePower(state);
        }
        appendTrendPointIfDue(state, now);
        state.dirty.set(true);
        state.lastUpdatedAt = now;
    }

    private void moveToOffline(ScopeState state, MeterState meter, Instant now) {
        boolean removed = state.active.remove(meter.meterId);
        boolean added = state.offline.add(meter.meterId);
        if (removed || added) {
            recalcDemand(state);
            appendTrendPointIfDue(state, now);
            state.dirty.set(true);
            state.lastUpdatedAt = now;
        }
    }

    private void recalcDemand(ScopeState state) {
        state.demand = sumActivePower(state);
    }

    private BigDecimal sumActivePower(ScopeState state) {
        BigDecimal sum = BigDecimal.ZERO;
        for (UUID meterId : state.active) {
            MeterState meter = meters.get(meterId);
            if (meter != null && meter.powerKw != null) {
                sum = sum.add(meter.powerKw);
            }
        }
        return sum;
    }

    private void appendTrendPointIfDue(ScopeState state, Instant now) {
        long resolutionMs = properties.getTrendResolutionMs();
        if (state.trend.isEmpty()
                || now.toEpochMilli() - state.trend.getLast().timestamp().toEpochMilli() >= resolutionMs) {
            state.trend.addLast(new TrendPoint(now, state.demand));
            while (state.trend.size() > properties.getTrendMaxPoints()) {
                state.trend.removeFirst();
            }
        }
    }

    private ScopeState scope(ScopeKey key) {
        return scopes.computeIfAbsent(key, ScopeState::new);
    }

    private LiveEnergySnapshot buildSnapshot(ScopeState state, ScopeKey key, Instant now) {
        List<MeterLiveReading> readings = state.active.stream()
                .map(meters::get)
                .filter(Objects::nonNull)
                .limit(properties.getMaxMeterReadingsPerSnapshot())
                .map(m -> new MeterLiveReading(m.meterId, m.meterName, m.powerKw, m.lastSeenAt,
                        m.online ? "ONLINE" : "OFFLINE"))
                .toList();

        // If there is room, include a sample of offline meters so the UI can
        // show the offline count without a separate call.
        int offlineSlots = properties.getMaxMeterReadingsPerSnapshot() - readings.size();
        if (offlineSlots > 0) {
            List<MeterLiveReading> offlineReadings = state.offline.stream()
                    .limit(offlineSlots)
                    .map(meters::get)
                    .filter(Objects::nonNull)
                    .map(m -> new MeterLiveReading(m.meterId, m.meterName, m.powerKw, m.lastSeenAt,
                            m.online ? "ONLINE" : "OFFLINE"))
                    .toList();
            List<MeterLiveReading> combined = new ArrayList<>(readings.size() + offlineReadings.size());
            combined.addAll(readings);
            combined.addAll(offlineReadings);
            readings = combined;
        }

        return new LiveEnergySnapshot(
                now,
                key.organizationId,
                key.siteId,
                key.buildingId,
                state.demand,
                state.active.size(),
                state.offline.size(),
                readings,
                List.copyOf(state.trend));
    }

    private void broadcast(ScopeKey key, LiveEnergySnapshot snapshot) {
        Set<SseEmitter> set = emitters.get(key);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            send(emitter, snapshot);
        }
    }

    private void send(SseEmitter emitter, LiveEnergySnapshot snapshot) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name("snapshot")
                    .id(snapshot.generatedAt().toString())
                    .data(snapshot);
            emitter.send(event);
        } catch (IOException e) {
            log.debug("Removing SSE emitter after send failure: {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private static final class ScopeKey {
        final UUID organizationId;
        final UUID siteId;
        final UUID buildingId;

        private ScopeKey(UUID organizationId, UUID siteId, UUID buildingId) {
            this.organizationId = organizationId;
            this.siteId = siteId;
            this.buildingId = buildingId;
        }

        static ScopeKey of(UUID organizationId, UUID siteId, UUID buildingId) {
            return new ScopeKey(organizationId, siteId, buildingId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ScopeKey other)) return false;
            return Objects.equals(organizationId, other.organizationId)
                    && Objects.equals(siteId, other.siteId)
                    && Objects.equals(buildingId, other.buildingId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(organizationId, siteId, buildingId);
        }
    }

    private static final class ScopeState {
        final ScopeKey key;
        final Set<UUID> active = new LinkedHashSet<>();
        final Set<UUID> offline = new LinkedHashSet<>();
        final Deque<TrendPoint> trend = new ArrayDeque<>();
        final AtomicBoolean dirty = new AtomicBoolean(false);
        BigDecimal demand = BigDecimal.ZERO;
        Instant lastUpdatedAt;

        ScopeState(ScopeKey key) {
            this.key = key;
        }
    }

    private static final class MeterState {
        UUID meterId;
        UUID organizationId;
        UUID siteId;
        UUID buildingId;
        String meterName;
        BigDecimal powerKw;
        BigDecimal energyKwh;
        Instant lastSeenAt;
        volatile boolean online;
    }
}
