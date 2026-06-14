package io.jaiclaw.agentmind.tendencies.cadence;

/**
 * SPI for deciding whether a dialectic pass is due for a given
 * {@code (tenantId, userKey)} pair. Called by the SessionEndedEvent
 * dialectic trigger.
 *
 * <p>The default {@link TimeAndTurnCadenceGate} enforces both a minimum
 * interval ({@code min-interval=PT15M} default) and a minimum turn count
 * ({@code min-turns=5} default). Plugins can swap in turn-only or
 * interval-only policies.
 *
 * <p>Plan §8 task 3.6.
 */
public interface TendenciesCadenceGate {

    /**
     * Return {@code true} if a dialectic pass should run now for the given
     * user key. Implementations track per-user state internally; this
     * method is called from the dialectic trigger on each
     * SessionEndedEvent.
     *
     * @param tenantId      tenant key
     * @param userKey       per-user key (canonicalUserId or hash fallback)
     * @param sessionTurns  number of message turns in the just-ended
     *                      session (used by turn-threshold policies)
     */
    boolean shouldRun(String tenantId, String userKey, int sessionTurns);

    /**
     * Notify the gate that a dialectic pass just completed for the given
     * key. Implementations use this to update their min-interval clocks
     * and turn counters.
     */
    void recordRun(String tenantId, String userKey);

    /**
     * Snapshot the gate's hits/misses counters. Used by the Actuator
     * endpoint. Returns {@code (hits, misses)}.
     */
    record Stats(long hits, long misses) {}

    Stats stats();
}
