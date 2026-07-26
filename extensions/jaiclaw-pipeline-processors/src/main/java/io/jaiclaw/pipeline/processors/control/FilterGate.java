package io.jaiclaw.pipeline.processors.control;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * "Stop the pipeline unless X." Generalises the {@code escalationGate}
 * pattern from the support-triage-pipeline example. Supports three
 * predicate kinds:
 *
 * <ul>
 *   <li>{@code regex} — Java regex matched against the whole body</li>
 *   <li>{@code jsonpath} — JSONPath, true when the path resolves and
 *       is non-null</li>
 *   <li>{@code contains} — plain substring</li>
 * </ul>
 *
 * <p>On predicate failure:
 * <ul>
 *   <li>{@code onFail=stop-silently} (default) — sets a stop marker
 *       exchange property that a downstream {@code choice} route can
 *       branch on. Stage completes normally.</li>
 *   <li>{@code onFail=error} — throws {@link IllegalStateException}
 *       to trip the pipeline's {@link io.jaiclaw.pipeline.ErrorStrategy}.</li>
 * </ul>
 *
 * <p><b>Note:</b> "stop-silently" today only tags the exchange —
 * true early-exit in a Camel route requires the {@code switch} stage
 * type on the horizon in PIPELINE-STRATEGY.md § 3.4. Adopters can wire
 * a downstream check on {@code exchange.getProperty("jaiclaw.filter.stopped")}
 * until then.
 */
@PipelineProcessor(
        name = "Filter Gate",
        category = "Control",
        description = "Stop or fail the pipeline unless a predicate holds",
        icon = "gate")
public class FilterGate implements ConfigurableStageProcessor {

    private static final Logger log = LoggerFactory.getLogger(FilterGate.class);
    public static final String STOPPED_PROPERTY = "jaiclaw.filter.stopped";

    @Override
    public void process(Exchange exchange, StageDefinition stage,
                        PipelineContext context, Map<String, String> config) {
        String kind = config.getOrDefault("kind", "contains").toLowerCase();
        String predicate = config.get("predicate");
        String onFail = config.getOrDefault("onFail", "stop-silently").toLowerCase();
        String input = exchange.getIn().getBody(String.class);
        if (input == null) input = "";

        boolean passes = switch (kind) {
            case "regex"    -> predicate != null && Pattern.compile(predicate).matcher(input).find();
            case "jsonpath" -> jsonPathPasses(input, predicate);
            case "contains" -> predicate != null && input.contains(predicate);
            default         -> throw new IllegalArgumentException("Unknown filter kind: " + kind);
        };

        if (passes) return;  // fall through — pipeline continues normally

        switch (onFail) {
            case "error" -> throw new IllegalStateException(
                    "Filter Gate '" + stage.name() + "' rejected the input");
            case "stop-silently" -> {
                exchange.setProperty(STOPPED_PROPERTY, true);
                log.debug("Filter Gate '{}' stopped the pipeline silently", stage.name());
            }
            default -> throw new IllegalArgumentException("Unknown onFail: " + onFail);
        }
    }

    private static boolean jsonPathPasses(String input, String path) {
        if (path == null || path.isBlank() || input.isBlank()) return false;
        try {
            Object v = JsonPath.read(input, path);
            return v != null;
        } catch (PathNotFoundException e) {
            return false;
        }
    }

    @Override
    public String configSchema() {
        return """
                {
                  "type": "object",
                  "required": ["predicate"],
                  "properties": {
                    "kind":      { "type": "string", "enum": ["regex", "jsonpath", "contains"], "default": "contains" },
                    "predicate": { "type": "string", "description": "Regex / JSONPath / substring depending on kind" },
                    "onFail":    { "type": "string", "enum": ["stop-silently", "error"], "default": "stop-silently" }
                  }
                }""";
    }
}
