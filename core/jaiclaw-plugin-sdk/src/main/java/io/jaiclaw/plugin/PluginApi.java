package io.jaiclaw.plugin;

import io.jaiclaw.core.hook.HookHandler;
import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.core.tool.ToolCallback;

import java.util.Map;

/**
 * API surface exposed to plugin implementations during registration.
 */
public interface PluginApi {

    String id();

    String name();

    void registerTool(ToolCallback tool);

    <E, C> void on(HookName hookName, HookHandler<E, C> handler);

    <E, C> void on(HookName hookName, HookHandler<E, C> handler, int priority);

    Map<String, Object> pluginConfig();
}
