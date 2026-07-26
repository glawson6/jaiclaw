package io.jaiclaw.pipeline;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of {@link PipelineDefinition} instances.
 * Thread-safe via {@link ConcurrentHashMap}.
 */
public class PipelineRegistry {

    private final Map<String, PipelineDefinition> pipelines = new ConcurrentHashMap<>();

    /**
     * Register a pipeline definition. Silently replaces any existing
     * definition with the same ID — delegates to {@link #replace}.
     *
     * @param definition the pipeline definition to register
     */
    public void register(PipelineDefinition definition) {
        replace(definition);
    }

    /**
     * Register or replace a pipeline definition. Returns the previously
     * registered definition (or {@code null} if this was a fresh
     * registration).
     *
     * <p>The engine-side counterpart to the authoring plane's deploy
     * path: {@code PipelineLifecycleManager} (Phase 3) invokes this
     * after building the new routes.
     *
     * @param definition the pipeline definition to register
     * @return the previous definition for this id, or {@code null}
     */
    public PipelineDefinition replace(PipelineDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) {
            throw new IllegalArgumentException("PipelineDefinition and non-blank id required");
        }
        return pipelines.put(definition.id(), definition);
    }

    /**
     * Remove a pipeline definition. Returns the removed definition,
     * or {@code null} if no pipeline was registered for the id.
     *
     * <p>Callers are responsible for stopping/removing any Camel
     * routes the definition backed — {@code PipelineLifecycleManager}
     * (Phase 3) coordinates that side.
     *
     * @param id the pipeline id to remove
     * @return the removed definition, or {@code null}
     */
    public PipelineDefinition unregister(String id) {
        if (id == null || id.isBlank()) return null;
        return pipelines.remove(id);
    }

    /**
     * Get a pipeline definition by ID.
     *
     * @param id the pipeline ID
     * @return the definition, or null if not found
     */
    public PipelineDefinition get(String id) {
        return pipelines.get(id);
    }

    /**
     * Get all registered pipeline definitions.
     *
     * @return unmodifiable collection of all definitions
     */
    public Collection<PipelineDefinition> getAll() {
        return List.copyOf(pipelines.values());
    }

    /**
     * Get pipeline definitions accessible to the given tenant.
     * A pipeline is accessible if its tenantIds list is empty (global) or contains the tenant.
     *
     * @param tenantId the tenant identifier
     * @return list of accessible definitions
     */
    public List<PipelineDefinition> getForTenant(String tenantId) {
        return pipelines.values().stream()
                .filter(d -> d.tenantIds().isEmpty() || d.tenantIds().contains(tenantId))
                .toList();
    }

    /**
     * Check if a pipeline with the given ID is registered.
     *
     * @param id the pipeline ID
     * @return true if registered
     */
    public boolean contains(String id) {
        return pipelines.containsKey(id);
    }

    /**
     * Get the number of registered pipelines.
     */
    public int size() {
        return pipelines.size();
    }
}
