package io.jaiclaw.agentmind.tendencies.transcript;

import java.util.List;

/**
 * SPI the {@code TendenciesDialecticTrigger} uses to fetch the recent
 * transcript window for a just-ended session. Returning an empty list
 * causes the cadence gate to short-circuit on its min-turns threshold
 * (so the dialectic pass is skipped).
 *
 * <p>The default in-process implementation
 * ({@code InMemoryTranscriptSource}) tracks message strings keyed by
 * {@code sessionKey} via a hook on {@code MessageReceivedEvent}.
 * Production consumers can swap in an implementation backed by their
 * preferred transcript store (e.g. a JaiClaw {@code SessionStore} or a
 * Camel route consuming from Kafka).
 *
 * <p>Plan §8 task 3.9.
 */
public interface TranscriptSource {

    /**
     * Return the recent transcript window for a session.
     *
     * @param sessionKey  the just-ended session's key
     * @return ordered list of user message strings (most recent last);
     *         may be empty if the source has no record of the session
     */
    List<String> recentMessages(String sessionKey);
}
