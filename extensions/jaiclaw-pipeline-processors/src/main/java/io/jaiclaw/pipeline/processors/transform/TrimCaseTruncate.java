package io.jaiclaw.pipeline.processors.transform;

import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import org.apache.camel.Exchange;

import java.util.Map;

/**
 * Trivial string manipulator that composes the three most-common
 * "clean this up" operations: trim, case-transform, truncate. All three
 * apply in order when configured. Productises the {@code upperCase} /
 * {@code addExclaim} demo beans that ship with the pipeline-e2e example.
 */
@PipelineProcessor(
        name = "Trim / Case / Truncate",
        category = "Transform",
        description = "Trim whitespace, change case, and/or truncate to a max length",
        icon = "text")
public class TrimCaseTruncate implements ConfigurableStageProcessor {

    @Override
    public void process(Exchange exchange, StageDefinition stage,
                        PipelineContext context, Map<String, String> config) {
        String input = exchange.getIn().getBody(String.class);
        if (input == null) input = "";
        if (Boolean.parseBoolean(config.getOrDefault("trim", "false"))) {
            input = input.trim();
        }
        String caseOp = config.getOrDefault("case", "none");
        input = switch (caseOp.toLowerCase()) {
            case "upper" -> input.toUpperCase();
            case "lower" -> input.toLowerCase();
            case "title" -> toTitleCase(input);
            default -> input;
        };
        String maxLenRaw = config.get("maxLength");
        if (maxLenRaw != null && !maxLenRaw.isBlank()) {
            try {
                int maxLen = Integer.parseInt(maxLenRaw.trim());
                if (maxLen > 0 && input.length() > maxLen) {
                    input = input.substring(0, maxLen);
                }
            } catch (NumberFormatException ignored) {
                // maxLength misconfigured — pass through.
            }
        }
        exchange.getIn().setBody(input);
    }

    @Override
    public String configSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "trim":      { "type": "string", "default": "false", "enum": ["true", "false"] },
                    "case":      { "type": "string", "default": "none",  "enum": ["none", "upper", "lower", "title"] },
                    "maxLength": { "type": "string", "description": "Truncate to at most this many chars" }
                  }
                }""";
    }

    private static String toTitleCase(String input) {
        if (input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder(input.length());
        boolean upNext = true;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c)) {
                upNext = true;
                sb.append(c);
            } else if (upNext) {
                sb.append(Character.toUpperCase(c));
                upNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
