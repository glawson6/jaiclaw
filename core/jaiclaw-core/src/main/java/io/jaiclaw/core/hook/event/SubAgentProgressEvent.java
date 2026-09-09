package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired as a delegated child run makes progress, so a parent UI or a kanban card
 * can show something other than "running" during a long delegation.
 *
 * <p>Phase 2 of the 1.2.0 plan.
 *
 * @param agentId          agent running the child
 * @param sessionKey       the CHILD's session key
 * @param timestamp        when the update was emitted
 * @param handleId         id the parent can poll or cancel with
 * @param parentSessionKey the delegating run's session key
 * @param message          human-readable progress note
 */
@Experimental
public record SubAgentProgressEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String handleId,
        String parentSessionKey,
        String message
) implements HookEvent {

    public static SubAgentProgressEvent of(String agentId, String sessionKey, String handleId,
                                           String parentSessionKey, String message) {
        return new SubAgentProgressEvent(agentId, sessionKey, Instant.now(),
                handleId, parentSessionKey, message);
    }
}
