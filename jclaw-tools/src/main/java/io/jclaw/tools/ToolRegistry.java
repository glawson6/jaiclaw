package io.jclaw.tools;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolProfile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all tools available to the agent runtime.
 * Tools are registered by built-in components, plugins, and skills.
 */
public class ToolRegistry {

    private final Map<String, ToolCallback> tools = new ConcurrentHashMap<>();

    public void register(ToolCallback tool) {
        tools.put(tool.definition().name(), tool);
    }

    public void registerAll(Collection<? extends ToolCallback> callbacks) {
        callbacks.forEach(this::register);
    }

    public boolean unregister(String name) {
        return tools.remove(name) != null;
    }

    public Optional<ToolCallback> resolve(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolCallback> resolveAll() {
        return List.copyOf(tools.values());
    }

    public List<ToolCallback> resolveForProfile(ToolProfile profile) {
        return tools.values().stream()
                .filter(t -> t.definition().isAvailableIn(profile))
                .toList();
    }

    public List<ToolCallback> resolveBySection(String section) {
        return tools.values().stream()
                .filter(t -> section.equals(t.definition().section()))
                .toList();
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public Set<String> toolNames() {
        return Set.copyOf(tools.keySet());
    }

    public int size() {
        return tools.size();
    }

    public void clear() {
        tools.clear();
    }
}
