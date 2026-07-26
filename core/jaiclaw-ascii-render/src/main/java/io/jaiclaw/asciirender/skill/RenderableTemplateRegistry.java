package io.jaiclaw.asciirender.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Collects every {@link RenderableTemplate} bean in the Spring context by
 * {@link RenderableTemplate#name()} and exposes the lookup surface the
 * {@code RenderResponseTool} uses to dispatch LLM tool calls.
 *
 * <p>First-writer-wins on name collisions — a duplicate name is logged at
 * WARN so the adopter knows one of their templates was shadowed. Insertion
 * order is preserved (via {@link LinkedHashMap}) so {@link #names()}
 * returns names in registration order — deterministic + matches the tool's
 * JSON-schema {@code enum} ordering.
 *
 * <p>Register the registry as a Spring {@code @Bean} (typically via
 * {@code jaiclaw-spring-boot-starter}'s
 * {@code RenderableTemplatesAutoConfiguration}) with a
 * {@link List}{@code <RenderableTemplate>} collected from every template
 * bean in the context.
 */
public final class RenderableTemplateRegistry {

    private static final Logger log = LoggerFactory.getLogger(RenderableTemplateRegistry.class);

    private final LinkedHashMap<String, RenderableTemplate> byName = new LinkedHashMap<>();

    public RenderableTemplateRegistry(Collection<? extends RenderableTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            log.info("RenderableTemplateRegistry: 0 templates registered; render_response tool "
                    + "will error on every call until adopter registers at least one @Bean "
                    + "implementing RenderableTemplate.");
            return;
        }
        for (RenderableTemplate template : templates) {
            if (template == null) continue;
            String name = template.name();
            if (name == null || name.isBlank()) {
                log.warn("RenderableTemplate {} has a blank name — skipping.",
                        template.getClass().getName());
                continue;
            }
            RenderableTemplate existing = byName.get(name);
            if (existing != null) {
                log.warn("RenderableTemplate name '{}' collision: {} already registered; {} "
                        + "ignored (first-writer-wins). Rename one to disambiguate.",
                        name, existing.getClass().getName(), template.getClass().getName());
                continue;
            }
            byName.put(name, template);
        }
        log.info("RenderableTemplateRegistry: registered {} template(s): {}",
                byName.size(), byName.keySet());
    }

    /** Look up a template by name. Empty when unknown — caller handles the miss. */
    public Optional<RenderableTemplate> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(byName.get(name));
    }

    /** Names in insertion order. Empty when nothing was registered. */
    public Set<String> names() {
        return Collections.unmodifiableSet(byName.keySet());
    }

    /**
     * Union of every registered template's {@link RenderableTemplate#parameterNames()},
     * in registration order. Used to build the {@code render_response} tool's
     * union JSON schema — every param name that any template accepts becomes
     * an optional string property on the tool's schema, so the LLM can pass
     * any param that any template wants.
     */
    public Set<String> unionParameterNames() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (RenderableTemplate t : byName.values()) {
            Set<String> params = t.parameterNames();
            if (params == null) continue;
            for (String p : params) {
                if (p != null && !p.isBlank()) out.add(p);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public int size() {
        return byName.size();
    }
}
