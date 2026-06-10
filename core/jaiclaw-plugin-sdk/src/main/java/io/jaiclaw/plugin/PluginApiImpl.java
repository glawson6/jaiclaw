package io.jaiclaw.plugin;

import io.jaiclaw.core.hook.HookHandler;
import io.jaiclaw.core.hook.HookRegistration;
import io.jaiclaw.core.hook.event.HookEvent;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of PluginApi that delegates registrations to the
 * ToolRegistry and PluginRegistry.
 *
 * <p>0.8.0 hard-break: hook registrations are now keyed by the event
 * {@code Class<? extends HookEvent>}. See {@code docs/MIGRATION-0.8.md}
 * § P3.1.
 */
public class PluginApiImpl implements PluginApi {

    private final String pluginId;
    private final String pluginName;
    private final ToolRegistry toolRegistry;
    private final PluginRegistry pluginRegistry;
    private final Map<String, Object> config;
    private final CaffeinePluginStateStore stateStore;

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
        this.stateStore = new CaffeinePluginStateStore();
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
    public <E extends HookEvent> void on(Class<E> eventType, HookHandler<E> handler) {
        on(eventType, handler, HookRegistration.DEFAULT_PRIORITY);
    }

    @Override
    public <E extends HookEvent> void on(Class<E> eventType, HookHandler<E> handler, int priority) {
        var registration = new HookRegistration<>(pluginId, eventType, handler, priority, pluginId);
        pluginRegistry.addHook(registration);
        registeredHooks.add(eventType.getSimpleName());
    }

    @Override
    public Map<String, Object> pluginConfig() {
        return config;
    }

    @Override
    public PluginStateStore stateStore() {
        return stateStore;
    }

    public Set<String> registeredTools() { return Set.copyOf(registeredTools); }
    public Set<String> registeredHooks() { return Set.copyOf(registeredHooks); }
}
