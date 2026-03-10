package io.jclaw.plugin;

import io.jclaw.core.hook.HookHandler;
import io.jclaw.core.hook.HookName;
import io.jclaw.core.hook.HookRegistration;
import io.jclaw.core.tool.ToolCallback;
import io.jclaw.tools.ToolRegistry;

import java.util.*;

/**
 * Default implementation of PluginApi that delegates registrations to the
 * ToolRegistry and PluginRegistry.
 */
public class PluginApiImpl implements PluginApi {

    private final String pluginId;
    private final String pluginName;
    private final ToolRegistry toolRegistry;
    private final PluginRegistry pluginRegistry;
    private final Map<String, Object> config;

    private final Set<String> registeredTools = new LinkedHashSet<>();
    private final Set<String> registeredHooks = new LinkedHashSet<>();

    public PluginApiImpl(String pluginId, String pluginName,
                         ToolRegistry toolRegistry, PluginRegistry pluginRegistry,
                         Map<String, Object> config) {
        this.pluginId = pluginId;
        this.pluginName = pluginName;
        this.toolRegistry = toolRegistry;
        this.pluginRegistry = pluginRegistry;
        this.config = config != null ? Map.copyOf(config) : Map.of();
    }

    @Override
    public String id() { return pluginId; }

    @Override
    public String name() { return pluginName; }

    @Override
    public void registerTool(ToolCallback tool) {
        toolRegistry.register(tool);
        registeredTools.add(tool.definition().name());
    }

    @Override
    public <E, C> void on(HookName hookName, HookHandler<E, C> handler) {
        on(hookName, handler, HookRegistration.DEFAULT_PRIORITY);
    }

    @Override
    public <E, C> void on(HookName hookName, HookHandler<E, C> handler, int priority) {
        var registration = new HookRegistration<>(pluginId, hookName, handler, priority, pluginId);
        pluginRegistry.addHook(registration);
        registeredHooks.add(hookName.name());
    }

    @Override
    public Map<String, Object> pluginConfig() {
        return config;
    }

    public Set<String> registeredTools() { return Set.copyOf(registeredTools); }
    public Set<String> registeredHooks() { return Set.copyOf(registeredHooks); }
}
