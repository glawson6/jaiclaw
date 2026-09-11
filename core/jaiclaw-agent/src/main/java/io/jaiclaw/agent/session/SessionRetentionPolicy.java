package io.jaiclaw.agent.session;

import io.jaiclaw.core.api.Experimental;

import java.time.Duration;

/**
 * Bounds on how much live session state a {@link SessionManager} keeps.
 *
 * <p>Before 1.2.0 {@code InMemorySessionManager} held every session for the
 * lifetime of the process: an unbounded map that only ever shrank when something
 * explicitly closed or reset a session. A long-running gateway therefore
 * accumulated every conversation it had ever served, in heap, until restart.
 * That is the leak this record exists to close.
 *
 * <p>{@link #unlimited()} reproduces exactly the old behaviour and remains the
 * default, so enabling bounds is a deliberate operator choice rather than a
 * surprise on upgrade — silently dropping sessions from under a running
 * deployment would be the worse failure.
 *
 * <p>Eviction is <strong>idle-first, then size</strong>. Dropping the
 * longest-idle session is almost always closer to correct than dropping the
 * largest or the oldest-created: a conversation nobody has touched in a day is
 * far likelier to be finished than one that started a day ago and is still going.
 *
 * @param maxSessions        maximum live sessions; {@code <= 0} means unlimited
 * @param idleTimeout        evict sessions untouched for this long; null or
 *                           non-positive disables idle eviction
 * @param maxMessagesPerSession cap on one session's message list, so a single
 *                           runaway conversation cannot exhaust heap on its own;
 *                           {@code <= 0} means unlimited. Oldest messages are
 *                           dropped first, which loses the least useful context.
 *
 * <p>Phase 1.2.0 follow-up — session retention.
 */
@Experimental
public record SessionRetentionPolicy(
        int maxSessions,
        Duration idleTimeout,
        int maxMessagesPerSession
) {

    public SessionRetentionPolicy {
        if (maxSessions < 0) maxSessions = 0;
        if (idleTimeout != null && (idleTimeout.isZero() || idleTimeout.isNegative())) {
            idleTimeout = null;
        }
        if (maxMessagesPerSession < 0) maxMessagesPerSession = 0;
    }

    /** No bounds — the pre-1.2.0 behaviour, and the default. */
    public static SessionRetentionPolicy unlimited() {
        return new SessionRetentionPolicy(0, null, 0);
    }

    /**
     * A sensible bounded default for a long-running gateway: 10k sessions,
     * 24 hours idle, 500 messages each. Not applied automatically — offered so
     * adopters do not have to invent numbers.
     */
    public static SessionRetentionPolicy bounded() {
        return new SessionRetentionPolicy(10_000, Duration.ofHours(24), 500);
    }

    public boolean hasSessionLimit() {
        return maxSessions > 0;
    }

    public boolean hasIdleTimeout() {
        return idleTimeout != null;
    }

    public boolean hasMessageLimit() {
        return maxMessagesPerSession > 0;
    }

    /** True when this policy would never evict anything. */
    public boolean isUnlimited() {
        return !hasSessionLimit() && !hasIdleTimeout() && !hasMessageLimit();
    }
}
