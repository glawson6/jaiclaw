package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired when a delegated child run starts.
 *
 * <p>Phase 2 of the 1.2.0 plan (subagent delegation).
 *
 * @param agentId          agent running the child (same id as the parent)
 * @param sessionKey       the CHILD's session key
 * @param timestamp        when the child started
 * @param handleId         id the parent can poll or cancel with
 * @param parentSessionKey the delegating run's session key
 * @param goal             what the child was asked to do
 * @param depth            delegation depth of the child (parent depth + 1)
 * @param maxIterations    the child's iteration budget
 */
@Experimental
public record SubAgentStartedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String handleId,
        String parentSessionKey,
        String goal,
        int depth,
        int maxIterations
) implements HookEvent {

    public static SubAgentStartedEvent of(String agentId, String sessionKey, String handleId,
                                          String parentSessionKey, String goal,
                                          int depth, int maxIterations) {
        return new SubAgentStartedEvent(agentId, sessionKey, Instant.now(),
                handleId, parentSessionKey, goal, depth, maxIterations);
    }
}
