package io.jaiclaw.tools;

import io.jaiclaw.core.tool.CompositeToolProfile;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.tools.search.ToolSearchIndex;
import io.jaiclaw.tools.search.ToolSourceResolver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Central registry for all tools available to the agent runtime.
 * Tools are registered by built-in components, plugins, and skills.
 */
public class ToolRegistry {

    private final Map<String, ToolCallback> tools = new ConcurrentHashMap<>();

    /**
     * Names marked deferred. Held here rather than on the ToolDefinition because
     * {@code definition()} is owned by each tool implementation — the registry
     * cannot rewrite what a tool reports about itself, and rewrapping every
     * callback to change one flag would obscure the tool's real type from
     * {@code instanceof} checks elsewhere.
     */
    private final Set<String> deferred = ConcurrentHashMap.newKeySet();

    /** Rebuilt lazily on the next search after any registration change. */
    private volatile ToolSearchIndex index;

    public void register(ToolCallback tool) {
        tools.put(tool.definition().name(), tool);
        // A tool that declares itself deferred is honoured without configuration.
        if (tool.definition().deferred()) deferred.add(tool.definition().name());
        index = null;
    }

    public void registerAll(Collection<? extends ToolCallback> callbacks) {
        callbacks.forEach(this::register);
    }

    public boolean unregister(String name) {
        deferred.remove(name);
        index = null;
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

    /**
     * Resolve tools for a composite profile: union tools from all constituent base profiles,
     * then apply the composite's own deny list, then the composite's own allow list.
     */
    public List<ToolCallback> resolveForComposite(CompositeToolProfile composite) {
        // Union tools from all base profiles (deduplicate by name)
        Map<String, ToolCallback> union = new LinkedHashMap<>();
        for (ToolProfile base : composite.profiles()) {
            for (ToolCallback tool : resolveForProfile(base)) {
                union.putIfAbsent(tool.definition().name(), tool);
            }
        }

        Stream<ToolCallback> stream = union.values().stream();

        // Apply composite deny (deny-wins)
        if (!composite.deny().isEmpty()) {
            Set<String> denySet = Set.copyOf(composite.deny());
            stream = stream.filter(t -> !denySet.contains(t.definition().name()));
        }

        // Apply composite allow (keep only these if non-empty)
        if (!composite.allow().isEmpty()) {
            Set<String> allowSet = Set.copyOf(composite.allow());
            stream = stream.filter(t -> allowSet.contains(t.definition().name()));
        }

        return stream.toList();
    }

    /**
     * Resolve tools for a composite profile, then layer agent-level allow/deny on top.
     */
    public List<ToolCallback> resolveForCompositePolicy(CompositeToolProfile composite,
                                                         List<String> agentAllow,
                                                         List<String> agentDeny) {
        List<ToolCallback> compositeTools = resolveForComposite(composite);
        if ((agentAllow == null || agentAllow.isEmpty()) && (agentDeny == null || agentDeny.isEmpty())) {
            return compositeTools;
        }

        return compositeTools.stream()
                .filter(t -> {
                    String name = t.definition().name();
                    if (agentDeny != null && agentDeny.contains(name)) return false;
                    if (agentAllow != null && !agentAllow.isEmpty()) return agentAllow.contains(name);
                    return true;
                })
                .toList();
    }

    public List<ToolCallback> resolveBySection(String section) {
        return tools.values().stream()
                .filter(t -> section.equals(t.definition().section()))
                .toList();
    }

    // ─── Tool Search / deferred schemas (1.2.0 Phase 3) ──────────────────────

    /**
     * Marks every currently-registered tool matching {@code predicate} as deferred:
     * its schema is withheld from the model until {@code tool_search} surfaces it.
     *
     * <p>Deferral is a <strong>context-economy</strong> control, not an
     * authorization one. A deferred tool is still fully permitted — profile and
     * policy filtering remain the security boundary. Use
     * {@code resolveActive} to get the list actually sent to the model.
     *
     * @return how many tools were newly deferred
     */
    public int markDeferred(Predicate<ToolDefinition> predicate) {
        if (predicate == null) return 0;
        int count = 0;
        for (ToolCallback tool : tools.values()) {
            ToolDefinition def = tool.definition();
            if (predicate.test(def) && deferred.add(def.name())) count++;
        }
        if (count > 0) index = null;
        return count;
    }

    /**
     * Like {@link #markDeferred(Predicate)}, but the predicate also receives the
     * tool's <em>resolved</em> source.
     *
     * <p>Most tools do not stamp {@link ToolDefinition#source()} themselves — it
     * is derived from the implementing class's package by
     * {@link ToolSourceResolver}. Rules keyed on source must therefore go through
     * this overload, or they would only ever match the handful of tools that set
     * the field explicitly.
     *
     * @param predicate receives {@code (definition, resolvedSource)}
     * @return how many tools were newly deferred
     */
    public int markDeferred(java.util.function.BiPredicate<ToolDefinition, String> predicate) {
        if (predicate == null) return 0;
        int count = 0;
        for (ToolCallback tool : tools.values()) {
            ToolDefinition def = tool.definition();
            String source = ToolSourceResolver.resolve(tool);
            if (predicate.test(def, source) && deferred.add(def.name())) count++;
        }
        if (count > 0) index = null;
        return count;
    }

    /** The resolved source of a registered tool; {@code builtin} when unknown. */
    public String sourceOf(String toolName) {
        ToolCallback tool = tools.get(toolName);
        return tool == null ? ToolDefinition.SOURCE_BUILTIN : ToolSourceResolver.resolve(tool);
    }

    /** Clears deferral for a single tool, so its schema is sent up front again. */
    public boolean clearDeferred(String name) {
        boolean removed = deferred.remove(name);
        if (removed) index = null;
        return removed;
    }

    /** Clears every deferral. */
    public void clearAllDeferred() {
        if (!deferred.isEmpty()) {
            deferred.clear();
            index = null;
        }
    }

    /** True when this tool's schema is withheld until discovered. */
    public boolean isDeferred(String name) {
        return deferred.contains(name);
    }

    /** Names of all deferred tools. */
    public Set<String> deferredNames() {
        return Set.copyOf(deferred);
    }

    /**
     * The tools whose schemas should be sent to the model this turn: everything
     * permitted by {@code profile} that is <em>not</em> deferred, plus any
     * deferred tools this session has already discovered.
     *
     * <p>With no deferrals configured this returns exactly what
     * {@link #resolveForProfile(ToolProfile)} returns, in the same order — the
     * feature is inert until switched on.
     *
     * @param profile             the run's tool profile
     * @param sessionDiscoveries  names surfaced earlier in this session; may be null
     */
    public List<ToolCallback> resolveActive(ToolProfile profile, Set<String> sessionDiscoveries) {
        List<ToolCallback> permitted = resolveForProfile(profile);
        if (deferred.isEmpty()) return permitted;
        Set<String> discovered = sessionDiscoveries == null ? Set.of() : sessionDiscoveries;
        return permitted.stream()
                .filter(t -> {
                    String name = t.definition().name();
                    return !deferred.contains(name) || discovered.contains(name);
                })
                .toList();
    }

    /**
     * Same as {@link #resolveActive(ToolProfile, Set)} but honouring allow/deny
     * policy as well.
     */
    public List<ToolCallback> resolveActiveForPolicy(ToolProfile profile, List<String> allow,
                                                     List<String> deny, Set<String> sessionDiscoveries) {
        List<ToolCallback> permitted = resolveForPolicy(profile, allow, deny);
        if (deferred.isEmpty()) return permitted;
        Set<String> discovered = sessionDiscoveries == null ? Set.of() : sessionDiscoveries;
        return permitted.stream()
                .filter(t -> {
                    String name = t.definition().name();
                    return !deferred.contains(name) || discovered.contains(name);
                })
                .toList();
    }

    /**
     * Searches tool metadata, restricted to what {@code profile} permits.
     *
     * <p>Searches every permitted tool, not only deferred ones: a model asking
     * "is there a tool for X" should get a truthful answer whether or not X
     * happened to be deferred.
     *
     * @param query free text
     * @param profile the run's profile — results never exceed it
     * @param limit maximum results
     */
    public List<ToolDefinition> search(String query, ToolProfile profile, int limit) {
        ToolSearchIndex current = index;
        if (current == null) {
            current = ToolSearchIndex.of(tools.values().stream()
                    .map(ToolCallback::definition)
                    .toList());
            index = current;
        }
        List<ToolDefinition> hits = current.search(query, Math.max(limit, 0) * 4);
        List<ToolDefinition> allowed = new ArrayList<>();
        for (ToolDefinition d : hits) {
            if (d.isAvailableIn(profile)) allowed.add(d);
            if (allowed.size() >= limit) break;
        }
        return List.copyOf(allowed);
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
        deferred.clear();
        index = null;
        tools.clear();
    }
}
