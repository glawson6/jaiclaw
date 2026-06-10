package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.model.AssistantMessage;

import java.time.Instant;

/**
 * Fired after {@code AgentRuntime} produces its final assistant message.
 *
 * <p>Replaces the pre-0.8.0 {@code AGENT_END} hook payload (which was the
 * raw {@link AssistantMessage}).
 */
public record AgentEndedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        AssistantMessage assistantMessage
) implements HookEvent {

    public static AgentEndedEvent of(String agentId, String sessionKey, AssistantMessage assistantMessage) {
        return new AgentEndedEvent(agentId, sessionKey, Instant.now(), assistantMessage);
    }
}
