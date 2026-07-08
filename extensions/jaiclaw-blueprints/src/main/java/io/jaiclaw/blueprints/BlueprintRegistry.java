package io.jaiclaw.blueprints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Central registry of blueprints, populated from every configured source
 * (Java-code {@link Blueprints} providers + YAML files loaded by
 * {@link BlueprintYamlLoader}). The registry deduplicates by
 * {@link BlueprintDefinition#id()}: code wins over YAML, with a warning
 * logged for each override so operators notice the conflict.
 *
 * <p>Thread-safety: registry contents are immutable after {@link #register}
 * completes. Callers read via {@link #all}, {@link #byId}, and
 * {@link #byCategory}.
 */
public class BlueprintRegistry {

    private static final Logger log = LoggerFactory.getLogger(BlueprintRegistry.class);

    private final Map<String, BlueprintDefinition> byId = new LinkedHashMap<>();

    /**
     * Add blueprints. Later calls with the same id log a warning and skip
     * (the first definition wins). To force replacement, call {@link #clear}
     * first.
     */
    public synchronized void register(List<BlueprintDefinition> defs, String source) {
        if (defs == null) return;
        for (BlueprintDefinition d : defs) {
            if (byId.containsKey(d.id())) {
                log.warn("Blueprint '{}' from {} is a duplicate — keeping existing definition",
                        d.id(), source);
                continue;
            }
            byId.put(d.id(), d);
            log.debug("Registered blueprint '{}' from {}", d.id(), source);
        }
    }

    /** Empty the registry — mostly for tests. */
    public synchronized void clear() {
        byId.clear();
    }

    public synchronized List<BlueprintDefinition> all() {
        return List.copyOf(byId.values());
    }

    public synchronized Optional<BlueprintDefinition> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** All blueprints in a category, insertion-ordered within the category. */
    public synchronized List<BlueprintDefinition> byCategory(String category) {
        if (category == null) return List.of();
        List<BlueprintDefinition> hits = new ArrayList<>();
        for (BlueprintDefinition d : byId.values()) {
            if (category.equals(d.category())) hits.add(d);
        }
        return List.copyOf(hits);
    }

    /** Sorted set of all categories represented in the registry. */
    public synchronized Set<String> categories() {
        TreeSet<String> cats = new TreeSet<>();
        for (BlueprintDefinition d : byId.values()) cats.add(d.category());
        return Collections.unmodifiableSet(cats);
    }

    public synchronized int size() {
        return byId.size();
    }
}
