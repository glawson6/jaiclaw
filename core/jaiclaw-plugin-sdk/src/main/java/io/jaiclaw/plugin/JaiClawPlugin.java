package io.jaiclaw.plugin;

import io.jaiclaw.core.plugin.PluginDefinition;

/**
 * SPI for JaiClaw plugins. Implement this interface and register via Spring component scanning
 * or {@code META-INF/services/io.jaiclaw.plugin.JaiClawPlugin}.
 */
public interface JaiClawPlugin {

    PluginDefinition definition();

    void register(PluginApi api);

    default void activate(PluginApi api) {}

    default void deactivate() {}
}
