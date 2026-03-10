package io.jclaw.plugin;

import io.jclaw.core.hook.HookName;
import io.jclaw.core.hook.HookRegistration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aggregates all plugin registrations — tools, hooks, services.
 */
public class PluginRegistry {

    private final List<PluginRecord> plugins = new CopyOnWriteArrayList<>();
    private final List<HookRegistration<?, ?>> hooks = new CopyOnWriteArrayList<>();

    public void addPlugin(PluginRecord record) {
        plugins.add(record);
    }

    public void addHook(HookRegistration<?, ?> registration) {
        hooks.add(registration);
    }

    public List<PluginRecord> plugins() {
        return List.copyOf(plugins);
    }

    public List<HookRegistration<?, ?>> hooks() {
        return List.copyOf(hooks);
    }

    public Optional<PluginRecord> findPlugin(String id) {
        return plugins.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public List<HookRegistration<?, ?>> hooksFor(HookName hookName) {
        return hooks.stream().filter(h -> h.hookName() == hookName).toList();
    }

    public int pluginCount() {
        return plugins.size();
    }

    public int hookCount() {
        return hooks.size();
    }
}
