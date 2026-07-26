package io.jaiclaw.pipeline.processors.transform;

import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.TemplateResolver;
import org.apache.camel.Exchange;

import java.util.Map;

/**
 * Thin wrapper over {@link TemplateResolver} — renders a caller-supplied
 * template string against the current pipeline context. Makes templating
 * a first-class palette node rather than "only inside prompts + output.template".
 *
 * <p>Config keys:
 * <ul>
 *   <li>{@code template} — the template string. Supports every
 *       placeholder {@link TemplateResolver} does:
 *       {@code {{stages.X.output}}}, {@code {{stages.X.metadata.k}}},
 *       {@code {{input}}}, {@code {{pipeline.*}}}. If missing, the
 *       stage-input body is returned unchanged.</li>
 * </ul>
 */
@PipelineProcessor(
        name = "Template",
        category = "Transform",
        description = "Render a template string against the current pipeline context",
        icon = "template")
public class TemplateProcessor implements ConfigurableStageProcessor {

    @Override
    public void process(Exchange exchange, StageDefinition stage,
                        PipelineContext context, Map<String, String> config) {
        String template = config.get("template");
        if (template == null || template.isBlank()) {
            // No template configured — pass through.
            return;
        }
        String rendered = TemplateResolver.resolve(template, context);
        exchange.getIn().setBody(rendered == null ? "" : rendered);
    }

    @Override
    public String configSchema() {
        return """
                {
                  "type": "object",
                  "required": ["template"],
                  "properties": {
                    "template": {
                      "type": "string",
                      "description": "Template with {{stages.X.output}}, {{input}}, {{pipeline.*}} placeholders"
                    }
                  }
                }""";
    }
}
