package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired when the tool loop observes the same tool called with identical
 * arguments {@code repetitionThreshold} times consecutively — the signature of a
 * model stuck in a loop.
 *
 * <p>On the first trip the loop injects a corrective notice as the tool result
 * and lets the model choose another approach. If repetition continues past
 * {@code threshold + 2} the loop forces a tool-less final turn. This event is
 * fired on the first trip only.
 *
 * <p>Phase 1 of the 1.2.0 plan (runtime guards).
 *
 * @param agentId     agent whose run is repeating
 * @param sessionKey  session the run belongs to
 * @param timestamp   when repetition was detected
 * @param toolName    the tool being called repeatedly
 * @param repeatCount how many identical consecutive calls were seen
 * @param forcedFinal whether this detection forced the tool-less final turn
 */
@Experimental
public record RepetitionDetectedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String toolName,
        int repeatCount,
        boolean forcedFinal
) implements HookEvent {

    public static RepetitionDetectedEvent of(String agentId, String sessionKey,
                                             String toolName, int repeatCount, boolean forcedFinal) {
        return new RepetitionDetectedEvent(agentId, sessionKey, Instant.now(),
                toolName, repeatCount, forcedFinal);
    }
}
