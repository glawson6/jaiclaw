package io.jclaw.plugin;

import io.jclaw.core.plugin.PluginDefinition;

/**
 * SPI for JClaw plugins. Implement this interface and register via Spring component scanning
 * or {@code META-INF/services/io.jclaw.plugin.JClawPlugin}.
 */
public interface JClawPlugin {

    PluginDefinition definition();

    void register(PluginApi api);

    default void activate(PluginApi api) {}

    default void deactivate() {}
}
