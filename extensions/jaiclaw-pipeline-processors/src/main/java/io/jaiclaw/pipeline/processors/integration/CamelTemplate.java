package io.jaiclaw.pipeline.processors.integration;

import java.util.Map;

/**
 * A palette entry describing a curated {@code CAMEL}-stage template —
 * a parameterised Camel URI pattern with a small inspector form and a
 * per-scheme allowlist hint. Templates are pure data (YAML on the
 * classpath) and require no Java per template — Studio substitutes
 * config values into {@code uriPattern} at "insert into canvas" time
 * to produce the stage's actual {@code uri}.
 *
 * @param id          stable identifier (used in URLs / catalog keys)
 * @param name        palette display name
 * @param description one-sentence hover text
 * @param scheme      Camel scheme, e.g. {@code kafka}, {@code smtp}. Must
 *                    also be on the pipeline-level {@code allowedUriSchemes}
 *                    for UI-authored deploys to succeed.
 * @param uriPattern  the URI template, with {@code {{config.field}}}
 *                    placeholders that the Studio inspector fills.
 * @param configSchema JSON Schema for the inspector form
 * @param icon        optional icon id
 */
public record CamelTemplate(
        String id,
        String name,
        String description,
        String scheme,
        String uriPattern,
        Map<String, Object> configSchema,
        String icon
) {
    public CamelTemplate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CamelTemplate id must not be blank");
        }
        if (name == null || name.isBlank()) name = id;
        if (description == null) description = "";
        if (scheme == null || scheme.isBlank()) {
            throw new IllegalArgumentException("CamelTemplate scheme must not be blank");
        }
        if (uriPattern == null || uriPattern.isBlank()) {
            throw new IllegalArgumentException("CamelTemplate uriPattern must not be blank");
        }
        if (configSchema == null) configSchema = Map.of();
        if (icon == null) icon = "";
    }
}
