package io.jaiclaw.pipeline.processors.transform;

import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import org.apache.camel.Exchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex processors — extract and replace. Kept in one file since they
 * share the same guard logic.
 */
public final class RegexProcessors {

    private RegexProcessors() {}

    @PipelineProcessor(
            name = "Regex Extract",
            category = "Transform",
            description = "Extract a regex group from the input; supports single-match or all-matches",
            icon = "regex")
    public static class Extract implements ConfigurableStageProcessor {

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) {
            String patternStr = config.get("pattern");
            if (patternStr == null || patternStr.isBlank()) {
                throw new IllegalArgumentException("Regex Extract requires 'pattern' config");
            }
            int group = parseGroup(config.get("group"));
            boolean allMatches = Boolean.parseBoolean(config.getOrDefault("allMatches", "false"));
            String input = exchange.getIn().getBody(String.class);
            if (input == null) input = "";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher m = pattern.matcher(input);
            if (allMatches) {
                List<String> hits = new ArrayList<>();
                while (m.find()) {
                    if (group < 0 || group > m.groupCount()) hits.add(m.group());
                    else hits.add(m.group(group));
                }
                exchange.getIn().setBody(String.join("\n", hits));
            } else {
                if (m.find() && group >= 0 && group <= m.groupCount()) {
                    exchange.getIn().setBody(m.group(group));
                } else if (m.find(0)) {
                    exchange.getIn().setBody(m.group());
                } else {
                    exchange.getIn().setBody("");
                }
            }
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "required": ["pattern"],
                      "properties": {
                        "pattern":    { "type": "string", "description": "Java regex" },
                        "group":      { "type": "string", "default": "0", "description": "Capture group index (0 = full match)" },
                        "allMatches": { "type": "string", "default": "false", "description": "true = newline-join every match" }
                      }
                    }""";
        }
    }

    @PipelineProcessor(
            name = "Regex Replace",
            category = "Transform",
            description = "Replace regex matches in the input",
            icon = "regex")
    public static class Replace implements ConfigurableStageProcessor {

        @Override
        public void process(Exchange exchange, StageDefinition stage,
                            PipelineContext context, Map<String, String> config) {
            String patternStr = config.get("pattern");
            if (patternStr == null || patternStr.isBlank()) {
                throw new IllegalArgumentException("Regex Replace requires 'pattern' config");
            }
            String replacement = config.getOrDefault("replacement", "");
            String input = exchange.getIn().getBody(String.class);
            if (input == null) input = "";
            String out = Pattern.compile(patternStr).matcher(input).replaceAll(replacement);
            exchange.getIn().setBody(out);
        }

        @Override
        public String configSchema() {
            return """
                    {
                      "type": "object",
                      "required": ["pattern"],
                      "properties": {
                        "pattern":     { "type": "string", "description": "Java regex" },
                        "replacement": { "type": "string", "default": "", "description": "Replacement — supports $1, $2 backrefs" }
                      }
                    }""";
        }
    }

    private static int parseGroup(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
