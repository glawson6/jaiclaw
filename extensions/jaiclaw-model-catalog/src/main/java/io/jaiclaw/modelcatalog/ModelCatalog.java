package io.jaiclaw.modelcatalog;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralized registry of model metadata with alias resolution and capability matching.
 */
public class ModelCatalog {

    private final List<ModelEntry> entries = new CopyOnWriteArrayList<>();

    public void register(ModelEntry entry) {
        // Remove existing entry with same id to allow override
        entries.removeIf(e -> e.id().equals(entry.id()));
        entries.add(entry);
    }

    public Optional<ModelEntry> resolve(String idOrAlias) {
        // Try exact id match first
        for (ModelEntry e : entries) {
            if (e.id().equals(idOrAlias)) return Optional.of(e);
        }
        // Try alias match
        for (ModelEntry e : entries) {
            if (e.aliases().contains(idOrAlias)) return Optional.of(e);
        }
        return Optional.empty();
    }

    public List<ModelEntry> findByCapabilities(Set<ModelCapability> required) {
        return entries.stream()
                .filter(e -> e.capabilities().containsAll(required))
                .toList();
    }

    public List<ModelEntry> findByCostTier(CostTier tier) {
        return entries.stream()
                .filter(e -> e.costTier() == tier)
                .toList();
    }

    public List<ModelEntry> findByProvider(String provider) {
        return entries.stream()
                .filter(e -> e.provider().equalsIgnoreCase(provider))
                .toList();
    }

    public List<ModelEntry> all() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }
}
