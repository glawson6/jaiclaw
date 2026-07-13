package io.jaiclaw.kanban.loader;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import io.jaiclaw.kanban.model.BoardDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Parses one board YAML file into a {@link BoardDefinition}. The filename
 * stem is used as the fallback {@code id} when the YAML omits one.
 */
public final class BoardYamlParser {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private BoardYamlParser() {}

    public static BoardDefinition parse(InputStream in, String fallbackId, String sourceUri)
            throws BoardLoadException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = YAML.readValue(in, Map.class);
            if (raw == null) {
                throw new BoardLoadException("board YAML at " + sourceUri + " is empty");
            }
            raw.computeIfAbsent("id", k -> fallbackId);
            return YAML.convertValue(raw, BoardDefinition.class);
        } catch (IOException | IllegalArgumentException e) {
            throw new BoardLoadException(
                    "failed to parse board YAML at " + sourceUri + ": " + e.getMessage(), e);
        }
    }
}
