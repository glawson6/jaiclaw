package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Modifying hook fired before the agent runtime sends the system prompt to
 * the LLM. Plugins can mutate the prompt by returning a new value; the
 * dispatcher chains modifying handlers in priority order.
 *
 * <p>The carried {@code systemPrompt} is the value as currently built (after
 * skill injection, workspace-memory injection, and tenant-specific overrides).
 * Plugins should return a new {@code BeforePromptBuildEvent} with the
 * modified prompt to alter it; returning {@code null} or the same event
 * leaves the prompt unchanged.
 *
 * <p>Replaces the pre-0.8.0 {@code BEFORE_PROMPT_BUILD} modifying hook
 * payload (which was the raw {@code String systemPrompt}).
 */
public record BeforePromptBuildEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String systemPrompt
) implements HookEvent {

    public static BeforePromptBuildEvent of(String agentId, String sessionKey, String systemPrompt) {
        return new BeforePromptBuildEvent(agentId, sessionKey, Instant.now(), systemPrompt);
    }

    /** Return a copy of this event with the prompt rewritten. */
    public BeforePromptBuildEvent withSystemPrompt(String newPrompt) {
        return new BeforePromptBuildEvent(agentId, sessionKey, timestamp, newPrompt);
    }
}
