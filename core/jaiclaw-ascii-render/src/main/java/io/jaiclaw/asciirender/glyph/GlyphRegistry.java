package io.jaiclaw.asciirender.glyph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * Named lookup surface for glyphs. Seeded with the built-in
 * {@link GlyphSet#defaults()} and extended at framework startup by
 * merging every {@link GlyphContribution} bean found in the Spring
 * context. Last-write-wins on name collisions — a duplicate name is
 * logged at INFO so operators can trace the override to the responsible
 * contribution.
 *
 * <p>A pure-Java class — no Spring dep — so callers can construct one
 * directly in tests or scripts:
 * <pre>{@code
 * GlyphRegistry r = GlyphRegistry.defaults();
 * r.register(new GlyphDefinition("thunder", "⚡",
 *         GlyphSemanticClass.INFO, "Fast path"));
 * }</pre>
 *
 * <p>The shared framework instance lives at
 * {@link GlyphRegistry#global()} — populated by
 * {@code GlyphRegistryAutoConfiguration} in {@code jaiclaw-tools}.
 * Callers that do not use Spring can install their own global via
 * {@link GlyphRegistry#setGlobal(GlyphRegistry)}.
 */
public final class GlyphRegistry {

    private static final Logger log = LoggerFactory.getLogger(GlyphRegistry.class);

    private static volatile GlyphRegistry global = defaults();

    private final LinkedHashMap<String, GlyphDefinition> byName = new LinkedHashMap<>();

    /** Empty registry. Prefer {@link #defaults()} unless you specifically want no built-ins. */
    public GlyphRegistry() {
    }

    /**
     * Return a new registry seeded with the built-in
     * {@link GlyphSet#defaults()} vocabulary.
     */
    public static GlyphRegistry defaults() {
        GlyphRegistry r = new GlyphRegistry();
        for (GlyphDefinition g : GlyphSet.defaults()) {
            r.byName.put(g.name(), g);
        }
        return r;
    }

    /**
     * Register a glyph. Overrides an existing entry with the same name
     * (last-write-wins) and logs the override at INFO.
     */
    public void register(GlyphDefinition definition) {
        if (definition == null) return;
        GlyphDefinition prior = byName.put(definition.name(), definition);
        if (prior != null && !prior.equals(definition)) {
            log.info("GlyphRegistry: '{}' overridden — was {}, now {}",
                    definition.name(), prior.glyph(), definition.glyph());
        }
    }

    /**
     * Look up a glyph by name. Returns {@link Optional#empty()} when
     * the name is unknown — callers surface the miss.
     */
    public Optional<GlyphDefinition> resolve(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(byName.get(name));
    }

    /** All registered glyphs in insertion order. Unmodifiable view. */
    public Collection<GlyphDefinition> list() {
        return Collections.unmodifiableCollection(byName.values());
    }

    /** Registered names in insertion order. Unmodifiable view. */
    public Set<String> names() {
        return Collections.unmodifiableSet(byName.keySet());
    }

    public int size() {
        return byName.size();
    }

    // ── shared framework registry ────────────────────────────────────

    /**
     * The framework-wide {@link GlyphRegistry} shared across every
     * element builder that resolves a glyph {@code name}. Populated by
     * {@code GlyphRegistryAutoConfiguration} at Spring startup; a pure
     * defaults-only registry is used when no auto-config runs (e.g.
     * unit tests that construct {@code AsciiSceneFactory} directly).
     */
    public static GlyphRegistry global() {
        return global;
    }

    /**
     * Replace the shared registry — called by the framework's
     * auto-config once adopter {@link GlyphContribution} beans have
     * been merged in. Also usable from tests that need to install a
     * bespoke registry; call {@link #resetGlobal()} in the teardown.
     */
    public static void setGlobal(GlyphRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("global registry must not be null");
        }
        global = registry;
    }

    /** Restore the shared registry to a fresh defaults-only instance. */
    public static void resetGlobal() {
        global = defaults();
    }
}
