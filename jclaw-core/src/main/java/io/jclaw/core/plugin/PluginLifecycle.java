package io.jclaw.core.plugin;

/**
 * SPI for JClaw plugins. Implementations register tools, hooks, and services
 * during the {@link #register} phase and start background work in {@link #activate}.
 *
 * @see io.jclaw.plugin.PluginApi
 */
public interface PluginLifecycle {

    PluginDefinition definition();

    /**
     * Register tools, hooks, services, and commands with the plugin API.
     * Called during startup before the agent runtime is ready.
     */
    void register(Object api);

    /**
     * Activate the plugin — start services, open connections.
     * Called after all plugins have registered.
     */
    default void activate(Object api) {}

    /** Clean shutdown. */
    default void deactivate() {}
}
