package io.jclaw.plugin;

import io.jclaw.core.hook.HookHandler;
import io.jclaw.core.hook.HookName;
import io.jclaw.core.tool.ToolCallback;

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
