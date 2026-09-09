package io.jaiclaw.learning.review;

import io.jaiclaw.core.api.Experimental;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether a session is worth reviewing yet.
 *
 * <p>Two conditions, both required: enough turns to contain a pattern, and enough
 * elapsed time since the last review of that session. Without this, a chatty
 * session would trigger an LLM review after every single turn — the dominant cost
 * risk in this whole module.
 *
 * <p>Modelled on {@code TimeAndTurnCadenceGate} in {@code jaiclaw-agentmind-tendencies}.
 * Clock is injectable so curator and cadence behaviour can be tested without sleeping.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public class ReviewCadenceGate {

    /** Sessions tracked before the map is pruned. */
    private static final int MAX_TRACKED = 5_000;

    private final Duration minInterval;
    private final int minTurns;
    private final Clock clock;
    private final Map<String, Instant> lastRun = new ConcurrentHashMap<>();

    public ReviewCadenceGate(Duration minInterval, int minTurns) {
        this(minInterval, minTurns, Clock.systemUTC());
    }

    public ReviewCadenceGate(Duration minInterval, int minTurns, Clock clock) {
        this.minInterval = minInterval == null ? Duration.ofMinutes(5) : minInterval;
        this.minTurns = Math.max(1, minTurns);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * @param sessionKey   session being considered
     * @param sessionTurns how many turns it has accumulated
     */
    public boolean shouldRun(String sessionKey, int sessionTurns) {
        if (sessionKey == null || sessionTurns < minTurns) return false;
        Instant previous = lastRun.get(sessionKey);
        if (previous == null) return true;
        return Duration.between(previous, clock.instant()).compareTo(minInterval) >= 0;
    }

    /** Records that a review just ran for this session. */
    public void recordRun(String sessionKey) {
        if (sessionKey == null) return;
        if (lastRun.size() > MAX_TRACKED) prune();
        lastRun.put(sessionKey, clock.instant());
    }

    /** Forgets a session, e.g. when it closes. */
    public void forget(String sessionKey) {
        if (sessionKey != null) lastRun.remove(sessionKey);
    }

    public int trackedSessions() {
        return lastRun.size();
    }

    /** Drops entries older than twice the interval; bounded memory for long uptimes. */
    private void prune() {
        Instant cutoff = clock.instant().minus(minInterval.multipliedBy(2));
        lastRun.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }
}
