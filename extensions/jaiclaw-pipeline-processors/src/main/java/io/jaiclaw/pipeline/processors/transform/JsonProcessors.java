package io.jaiclaw.pipeline.processors.transform;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import org.apache.camel.Exchange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSON processors — path-extract + schema-validate. Both use Jackson 3
 * for round-tripping and jayway JSONPath / networknt JSON Schema for
 * the actual work.
 */
public final class JsonProcessors {

    private JsonProcessors() {}

    // Networknt uses a com.fasterxml.jackson ObjectMapper internally
    // for its schema tree — we keep one instance per JVM. Our own body
    // reading uses tools.jackson (Jackson 3, matching the rest of the
    // codebase).
    private static final com.fasterxml.jackson.databind.ObjectMapper LEGACY_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @PipelineProcessor(
            name = "JSON Path Extract",
            category = "Transform",
            description = "Extract a value from a JSON body using a JSONPath expression",
            icon = "json")
    public static class PathExtract implements ConfigurableStageProcessor {

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) {
            String path = config.get("path");
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("JSON Path Extract requires 'path' config");
            }
            String fallback = config.getOrDefault("default", "");
            String input = exchange.getIn().getBody(String.class);
            if (input == null || input.isBlank()) {
                exchange.getIn().setBody(fallback);
                return;
            }
            try {
                Object value = JsonPath.read(input, path);
                exchange.getIn().setBody(value == null ? fallback : value.toString());
            } catch (PathNotFoundException e) {
                exchange.getIn().setBody(fallback);
            }
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "required": ["path"],
                      "properties": {
                        "path":    { "type": "string", "description": "e.g. $.orders[0].id" },
                        "default": { "type": "string", "description": "Returned when the path resolves to null or is missing" }
                      }
                    }""";
        }
    }

    @PipelineProcessor(
            name = "JSON Validate",
            category = "Validate",
            description = "Validate the input JSON against a JSON Schema; fails the stage on mismatch",
            icon = "shield")
    public static class Validate implements ConfigurableStageProcessor {

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) throws Exception {
            String schemaJson = config.get("schema");
            if (schemaJson == null || schemaJson.isBlank()) {
                throw new IllegalArgumentException("JSON Validate requires 'schema' config");
            }
            String input = exchange.getIn().getBody(String.class);
            if (input == null) input = "";
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonSchema schema = factory.getSchema(schemaJson);
            com.fasterxml.jackson.databind.JsonNode node = LEGACY_MAPPER.readTree(input);
            Set<ValidationMessage> errors = schema.validate(node);
            if (!errors.isEmpty()) {
                String joined = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));
                throw new IllegalStateException(
                        "Stage '" + stage.name() + "' JSON validation failed: " + joined);
            }
            // On success, pass through unchanged.
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "required": ["schema"],
                      "properties": {
                        "schema": { "type": "string", "description": "JSON Schema (Draft 2020-12) as a string" }
                      }
                    }""";
        }
    }
}
