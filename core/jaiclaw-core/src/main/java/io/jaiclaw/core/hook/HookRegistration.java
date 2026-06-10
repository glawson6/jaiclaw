package io.jaiclaw.core.hook;

import io.jaiclaw.core.hook.event.HookEvent;

/**
 * A registered hook handler with priority and source metadata.
 *
 * <p>Pre-0.8.0 was keyed by {@link HookName}; now keyed by the event
 * {@code Class<? extends HookEvent>} so the dispatcher can route events by
 * type rather than enum discriminator. See {@code docs/MIGRATION-0.8.md}
 * § P3.1.
 *
 * @param <E> the {@link HookEvent} subtype this registration handles
 */
public record HookRegistration<E extends HookEvent>(
        String pluginId,
        Class<E> eventType,
        HookHandler<E> handler,
        int priority,
        String source
) {
    public static final int DEFAULT_PRIORITY = 100;

    public HookRegistration(String pluginId, Class<E> eventType, HookHandler<E> handler) {
        this(pluginId, eventType, handler, DEFAULT_PRIORITY, pluginId);
    }
}
