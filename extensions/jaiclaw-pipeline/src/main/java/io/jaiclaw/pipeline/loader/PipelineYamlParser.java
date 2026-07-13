package io.jaiclaw.pipeline.loader;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLFactory;
import io.jaiclaw.pipeline.PipelineDefinition;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a single per-file pipeline YAML document and binds it to a
 * {@link PipelineDefinition}. The YAML body <em>is</em> the definition: top
 * level keys are {@code id}, {@code name}, {@code stages}, {@code trigger},
 * {@code output}, etc.
 *
 * <p>When the YAML file omits {@code id}, the caller supplies a fallback
 * (typically the filename stem). The fallback is spliced into the parsed
 * tree before binding, so the {@link PipelineDefinition} compact constructor
 * never sees a blank id.
 */
final class PipelineYamlParser {

    /** Shared mapper; jackson ObjectMapper is thread-safe after configuration. */
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PipelineYamlParser() {}

    /**
     * Bind the given YAML stream to a {@link PipelineDefinition}.
     *
     * @param in           YAML input — caller is responsible for closing
     * @param fallbackId   id to use when the file omits {@code id} (typically
     *                     the filename stem; never null/blank)
     * @param resourceName a human-readable name for error messages (e.g. the
     *                     URI of the resource being parsed)
     */
    static PipelineDefinition parse(InputStream in, String fallbackId, String resourceName) {
        if (fallbackId == null || fallbackId.isBlank()) {
            // Caller should always pass a fallback; this is a programmer error.
            throw new PipelineLoadException("Internal: fallback id must not be blank for '" + resourceName + "'");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(in);
        } catch (IOException e) {
            throw new PipelineLoadException(
                    "Failed to parse pipeline file '" + resourceName + "': " + e.getMessage(), e);
        }
        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new PipelineLoadException("Pipeline file '" + resourceName + "' is empty");
        }
        if (!root.isObject()) {
            throw new PipelineLoadException(
                    "Pipeline file '" + resourceName + "' must be a YAML object, got " + root.getNodeType());
        }

        ObjectNode objectRoot = (ObjectNode) root;

        // Splice in the fallback id when the file didn't supply one.
        JsonNode idNode = objectRoot.get("id");
        if (idNode == null || idNode.isNull() || idNode.asText("").isBlank()) {
            objectRoot.put("id", fallbackId);
        }

        // The record's `boolean enabled` defaults to false when absent because
        // primitives can't be null-checked in the compact constructor. The
        // field's documented contract is "default: true", so we default it
        // here for per-file pipelines.
        if (!objectRoot.has("enabled")) {
            objectRoot.put("enabled", true);
        }

        try {
            return MAPPER.treeToValue(objectRoot, PipelineDefinition.class);
        } catch (IllegalArgumentException | IOException e) {
            throw new PipelineLoadException(
                    "Failed to bind pipeline definition from '" + resourceName + "': " + e.getMessage(), e);
        }
    }
}
