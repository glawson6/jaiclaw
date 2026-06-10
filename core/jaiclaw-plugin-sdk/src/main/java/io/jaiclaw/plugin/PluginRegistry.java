package io.jaiclaw.plugin;

import io.jaiclaw.core.hook.HookRegistration;
import io.jaiclaw.core.hook.event.HookEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aggregates all plugin registrations — tools, hooks, services.
 *
 * <p>0.8.0 hard-break: {@link HookRegistration} is now generic over a single
 * {@link HookEvent} type parameter (was {@code <E, C>}); the lookup key is
 * the event class rather than the pre-0.8.0 {@code HookName} enum.
 */
public class PluginRegistry {

    private final List<PluginRecord> plugins = new CopyOnWriteArrayList<>();
    private final List<HookRegistration<? extends HookEvent>> hooks = new CopyOnWriteArrayList<>();

    public void addPlugin(PluginRecord record) {
        plugins.add(record);
    }

    public void addHook(HookRegistration<? extends HookEvent> registration) {
        hooks.add(registration);
    }

    public List<PluginRecord> plugins() {
        return List.copyOf(plugins);
    }

    public List<HookRegistration<? extends HookEvent>> hooks() {
        return List.copyOf(hooks);
    }

    public Optional<PluginRecord> findPlugin(String id) {
        return plugins.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public <E extends HookEvent> List<HookRegistration<? extends HookEvent>> hooksFor(Class<E> eventType) {
        return hooks.stream().filter(h -> h.eventType().equals(eventType)).toList();
    }

    public int pluginCount() {
        return plugins.size();
    }

    public int hookCount() {
        return hooks.size();
    }
}
