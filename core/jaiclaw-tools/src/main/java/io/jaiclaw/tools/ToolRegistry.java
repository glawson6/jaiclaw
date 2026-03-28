package io.jaiclaw.tools;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolProfile;

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

    /**
     * Resolve tools by profile first, then apply allow/deny lists.
     * If allow list is non-empty, only those tools are included (from the profile-filtered set).
     * Deny list removes tools from the result regardless.
     */
    public List<ToolCallback> resolveForPolicy(ToolProfile profile, List<String> allow, List<String> deny) {
        var profileTools = resolveForProfile(profile);
        if ((allow == null || allow.isEmpty()) && (deny == null || deny.isEmpty())) {
            return profileTools;
        }

        return profileTools.stream()
                .filter(t -> {
                    String name = t.definition().name();
                    if (deny != null && deny.contains(name)) return false;
                    if (allow != null && !allow.isEmpty()) return allow.contains(name);
                    return true;
                })
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
