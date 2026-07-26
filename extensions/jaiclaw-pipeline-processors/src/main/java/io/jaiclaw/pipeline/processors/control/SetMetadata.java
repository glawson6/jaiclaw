package io.jaiclaw.pipeline.processors.control;

import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.PipelineRouteBuilder;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.TemplateResolver;
import org.apache.camel.Exchange;

import java.util.Map;

/**
 * Write one key/value pair to the current stage's
 * {@link io.jaiclaw.pipeline.PipelineContext.StageOutput#metadata()}, so
 * downstream stages + templates can read it via
 * {@code {{stages.<this-stage-name>.metadata.<key>}}}.
 *
 * <p>The value supports the same template placeholders as the Template
 * processor (so it can carry forward another stage's output, or format
 * the current input into a metadata field).
 *
 * <p>Under the hood: the processor writes an exchange property
 * {@code jaiclaw.stage-meta.<key>}; {@link PipelineRouteBuilder} copies
 * every such property into the stage metadata + clears the property so
 * downstream stages start clean.
 */
@PipelineProcessor(
        name = "Set Metadata",
        category = "Control",
        description = "Attach a template-rendered value to this stage's metadata for downstream reads",
        icon = "tag")
public class SetMetadata implements ConfigurableStageProcessor {

    @Override
    public void process(Exchange exchange, StageDefinition stage,
                        PipelineContext context, Map<String, String> config) {
        String key = config.get("key");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Set Metadata requires 'key' config");
        }
        String template = config.getOrDefault("value", "");
        String rendered = TemplateResolver.resolve(template, context);
        exchange.setProperty(PipelineRouteBuilder.STAGE_METADATA_PREFIX + key,
                rendered == null ? "" : rendered);
        // Pass through the body unchanged so downstream stages see the same
        // input.
    }

    @Override
    public String configSchema() {
        return """
                {
                  "type": "object",
                  "required": ["key"],
                  "properties": {
                    "key":   { "type": "string", "description": "Metadata key (readable as {{stages.<this>.metadata.<key>}})" },
                    "value": { "type": "string", "description": "Template value; supports {{stages.X.output}}, {{input}}, {{pipeline.*}}" }
                  }
                }""";
    }
}
