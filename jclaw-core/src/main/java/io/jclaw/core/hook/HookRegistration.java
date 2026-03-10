package io.jclaw.core.hook;

/**
 * A registered hook handler with priority and source metadata.
 */
public record HookRegistration<E, C>(
        String pluginId,
        HookName hookName,
        HookHandler<E, C> handler,
        int priority,
        String source
) {
    public static final int DEFAULT_PRIORITY = 100;

    public HookRegistration(String pluginId, HookName hookName, HookHandler<E, C> handler) {
        this(pluginId, hookName, handler, DEFAULT_PRIORITY, pluginId);
    }
}
