package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired when a delegated child run reaches a terminal state — completed, failed,
 * or cancelled. Always fired for a child that started, including on the failure
 * path, so listeners can close out whatever they opened on
 * {@link SubAgentStartedEvent}.
 *
 * <p>Phase 2 of the 1.2.0 plan.
 *
 * @param agentId          agent that ran the child
 * @param sessionKey       the CHILD's session key
 * @param timestamp        when the child finished
 * @param handleId         id the parent polled or cancelled with
 * @param parentSessionKey the delegating run's session key
 * @param status           terminal status name ({@code COMPLETED|FAILED|CANCELLED})
 * @param summary          the child's answer when it completed; null otherwise
 * @param iterationsUsed   tool-loop iterations the child consumed
 * @param error            why it failed or was cancelled; null on success
 */
@Experimental
public record SubAgentEndedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String handleId,
        String parentSessionKey,
        String status,
        String summary,
        int iterationsUsed,
        String error
) implements HookEvent {

    public static SubAgentEndedEvent of(String agentId, String sessionKey, String handleId,
                                        String parentSessionKey, String status,
                                        String summary, int iterationsUsed, String error) {
        return new SubAgentEndedEvent(agentId, sessionKey, Instant.now(),
                handleId, parentSessionKey, status, summary, iterationsUsed, error);
    }
}
