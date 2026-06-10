package io.jaiclaw.core.hook;

import io.jaiclaw.core.hook.event.HookEvent;

/**
 * Functional interface for hook handlers.
 *
 * <p>Pre-0.8.0 took two parameters ({@code event, context}); now single-arg
 * because the event carries all the context fields (agentId, sessionKey,
 * timestamp) by contract. See {@code docs/MIGRATION-0.8.md} § P3.1.
 *
 * @param <E> the {@link HookEvent} subtype this handler consumes
 */
@FunctionalInterface
public interface HookHandler<E extends HookEvent> {

    /**
     * Handle a typed lifecycle event.
     *
     * @param event the typed event payload
     * @return a replacement event for modifying hooks (e.g.,
     *         {@link io.jaiclaw.core.hook.event.BeforePromptBuildEvent#withSystemPrompt}),
     *         or {@code null} for void handlers (the common case)
     */
    E handle(E event);
}
