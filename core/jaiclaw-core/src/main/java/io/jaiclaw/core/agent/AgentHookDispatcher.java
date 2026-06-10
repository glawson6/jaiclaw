package io.jaiclaw.core.agent;

import io.jaiclaw.core.hook.event.HookEvent;

/**
 * SPI for dispatching lifecycle hooks during agent execution.
 * Decouples the agent runtime from the plugin SDK's {@code HookRunner}.
 *
 * <p>0.8.0 hard-break: hooks are now keyed by event {@code Class<? extends HookEvent>}
 * rather than the pre-0.8.0 {@code HookName} enum. See
 * {@code docs/MIGRATION-0.8.md} § P3.1.
 */
public interface AgentHookDispatcher {

    /**
     * Fire a void (non-modifying) hook — handlers run in parallel.
     *
     * @param event the typed event to dispatch; its class is the dispatch key
     */
    <E extends HookEvent> void fireVoid(E event);

    /**
     * Fire a modifying hook — handlers run sequentially, each receiving the
     * previous handler's output. Returns the final modified event.
     */
    <E extends HookEvent> E fireModifying(E event);

    /**
     * Check if any handlers are registered for the given event type.
     */
    <E extends HookEvent> boolean hasHandlers(Class<E> eventType);
}
