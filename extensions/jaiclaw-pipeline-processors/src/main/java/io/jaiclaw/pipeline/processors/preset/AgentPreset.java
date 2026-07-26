package io.jaiclaw.pipeline.processors.preset;

import java.util.Map;

/**
 * A palette entry describing an AGENT-stage preset — name + description +
 * prompt template + config-field schema + recommended timeout. Presets
 * are pure data (YAML on the classpath) and require no Java per
 * preset — the Studio inspector uses the {@code configSchema} to
 * render fields, and at pipeline-build time the values substitute into
 * {@code promptTemplate} to produce the stage's actual
 * {@code systemPrompt}.
 *
 * <p>Shape mirrors what {@code PipelineCatalogService} projects into
 * the "AI" palette group.
 *
 * @param id             stable identifier (used in URLs / catalog keys)
 * @param name           palette display name
 * @param description    one-sentence hover text
 * @param promptTemplate the system-prompt template. Placeholders like
 *                       {@code {{config.field}}} are substituted by the
 *                       Studio at "insert into canvas" time; runtime
 *                       template placeholders ({@code {{stages.X.output}}},
 *                       {@code {{input}}}) resolve later in the normal
 *                       agent-stage path.
 * @param configSchema   JSON Schema for the inspector form
 * @param timeoutSeconds recommended stage timeout, purely advisory
 *                       (adopters override in the stage config)
 * @param icon           optional icon id — the Studio maps well-known
 *                       ids to SVGs; blank falls back to default
 */
public record AgentPreset(
        String id,
        String name,
        String description,
        String promptTemplate,
        Map<String, Object> configSchema,
        Integer timeoutSeconds,
        String icon
) {
    public AgentPreset {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("AgentPreset id must not be blank");
        }
        if (name == null || name.isBlank()) name = id;
        if (description == null) description = "";
        if (promptTemplate == null) promptTemplate = "";
        if (configSchema == null) configSchema = Map.of();
        if (icon == null) icon = "";
    }
}
