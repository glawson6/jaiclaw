package io.jaiclaw.agentmind.tendencies.cadence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link TendenciesCadenceGate}. A dialectic pass is allowed only
 * when BOTH thresholds are met:
 *
 * <ul>
 *   <li>at least {@code minInterval} has elapsed since the last recorded
 *       pass for the {@code (tenantId, userKey)} pair, AND</li>
 *   <li>the just-ended session carried at least {@code minTurns} messages</li>
 * </ul>
 *
 * <p>State is held in-process per {@code (tenantId, userKey)}. A future
 * Phase 5 sub-task can persist this in the store layer if cross-instance
 * coordination is needed; for now in-process is sufficient (the dialectic
 * pipeline is single-host within a deployment).
 *
 * <p>Hits / misses counters power the {@code /actuator/agentmind/tendencies}
 * endpoint.
 *
 * <p>Plan §8 task 3.6.
 */
public class TimeAndTurnCadenceGate implements TendenciesCadenceGate {

    private final Duration minInterval;
    private final int minTurns;
    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> lastRunAt = new ConcurrentHashMap<>();

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public TimeAndTurnCadenceGate(Duration minInterval, int minTurns) {
        this(minInterval, minTurns, Clock.systemUTC());
    }

    /** Test-friendly constructor accepting an injectable clock. */
    public TimeAndTurnCadenceGate(Duration minInterval, int minTurns, Clock clock) {
        if (minInterval == null) throw new IllegalArgumentException("minInterval is required");
        if (minTurns < 1) throw new IllegalArgumentException("minTurns must be >= 1");
        this.minInterval = minInterval;
        this.minTurns = minTurns;
        this.clock = clock;
    }

    @Override
    public boolean shouldRun(String tenantId, String userKey, int sessionTurns) {
        if (sessionTurns < minTurns) {
            misses.incrementAndGet();
            return false;
        }
        Instant last = lastRunAt.get(keyFor(tenantId, userKey));
        if (last == null) {
            hits.incrementAndGet();
            return true;
        }
        if (Duration.between(last, Instant.now(clock)).compareTo(minInterval) < 0) {
            misses.incrementAndGet();
            return false;
        }
        hits.incrementAndGet();
        return true;
    }

    @Override
    public void recordRun(String tenantId, String userKey) {
        lastRunAt.put(keyFor(tenantId, userKey), Instant.now(clock));
    }

    @Override
    public Stats stats() {
        return new Stats(hits.get(), misses.get());
    }

    private static String keyFor(String tenantId, String userKey) {
        return tenantId + ":" + userKey;
    }
}
