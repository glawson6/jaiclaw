package io.jaiclaw.plugin;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.plugin.PluginDefinition;

/**
 * SPI for JaiClaw plugins. Implement this interface and register via Spring component scanning
 * or {@code META-INF/services/io.jaiclaw.plugin.JaiClawPlugin}.
 *
 * <p>0.8.0 P3.5: {@link Experimental} — the hook-event-typed
 * {@link PluginApi.on(Class, ...)} side just changed in P3.1.
 */
@Experimental
public interface JaiClawPlugin {

    PluginDefinition definition();

    void register(PluginApi api);

    default void activate(PluginApi api) {}

    default void deactivate() {}
}
