package io.jaiclaw.kanban.loader;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import io.jaiclaw.kanban.model.BoardDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Parses one board YAML file into a {@link BoardDefinition}. The filename
 * stem is used as the fallback {@code id} when the YAML omits one.
 */
public final class BoardYamlParser {

    // Jackson 3 flipped FAIL_ON_NULL_FOR_PRIMITIVES to true by default; board YAMLs
    // historically expect null → false for absent boolean fields (e.g. terminal).
    private static final ObjectMapper YAML = YAMLMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

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
        } catch (Exception e) {
            throw new BoardLoadException(
                    "failed to parse board YAML at " + sourceUri + ": " + e.getMessage(), e);
        }
    }
}
