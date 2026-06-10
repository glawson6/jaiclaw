package io.jaiclaw.core.hook.event;

import java.time.Instant;

/**
 * Fired before the agent runtime resolves which LLM model to use.
 *
 * <p>Currently aspirational — the framework does not yet fire this in
 * 0.8.0. Reserved for a future PR where multi-model routing decisions
 * become first-class. Plugins may register handlers against this type now;
 * the runtime will pick them up once firing is wired.
 */
public record BeforeModelResolveEvent(
        String agentId,
        String sessionKey,
        Instant timestamp
) implements HookEvent {

    public static BeforeModelResolveEvent of(String agentId, String sessionKey) {
        return new BeforeModelResolveEvent(agentId, sessionKey, Instant.now());
    }
}
