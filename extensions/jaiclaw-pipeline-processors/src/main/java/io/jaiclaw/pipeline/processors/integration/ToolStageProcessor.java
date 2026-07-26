package io.jaiclaw.pipeline.processors.integration;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.pipeline.ConfigurableStageProcessor;
import io.jaiclaw.pipeline.PipelineContext;
import io.jaiclaw.pipeline.PipelineProcessor;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.TemplateResolver;
import io.jaiclaw.tools.ToolRegistry;
import org.apache.camel.Exchange;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Run any {@link ToolCallback} from {@link ToolRegistry} as a pipeline
 * stage. Every JaiClaw tool + every MCP tool surfaced through the
 * registry is a pipeline node for free — high leverage per the
 * PIPELINE-STUDIO-ANALYSIS.md Tier-3 catalog.
 *
 * <p>Config keys:
 * <ul>
 *   <li>{@code tool} — the tool name (must resolve in {@link ToolRegistry#resolve}).</li>
 *   <li>{@code args} — JSON object of tool arguments. Template-rendered
 *       first so caller-time values can be substituted from
 *       {@code {{input}}} or {@code {{stages.X.output}}}. If missing
 *       or blank, an empty argument map is passed.</li>
 * </ul>
 *
 * <p>The tool's {@link ToolResult} content becomes the exchange body
 * on Success; an Error result throws {@link IllegalStateException} to
 * trip the pipeline's error strategy.
 */
@PipelineProcessor(
        name = "Tool Invoke",
        category = "Integration",
        description = "Invoke any JaiClaw ToolRegistry tool as a pipeline stage",
        icon = "wrench")
public class ToolStageProcessor implements ConfigurableStageProcessor {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ToolRegistry toolRegistry;

    public ToolStageProcessor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void process(Exchange exchange, StageDefinition stage,
                        PipelineContext context, Map<String, String> config) throws Exception {
        String toolName = config.get("tool");
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool Invoke requires 'tool' config");
        }
        Optional<ToolCallback> resolved = toolRegistry.resolve(toolName);
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tool '" + toolName + "' not found in ToolRegistry");
        }
        Map<String, Object> args = parseArgs(config.get("args"), context);
        ToolContext toolContext = ToolContext.builder()
                .agentId(context.pipelineId())
                .sessionKey(context.executionId())
                .sessionId(context.executionId())
                .workspaceDir(".")
                .build();
        ToolResult result = resolved.get().execute(args, toolContext);
        switch (result) {
            case ToolResult.Success s -> exchange.getIn().setBody(
                    s.content() == null ? "" : s.content());
            case ToolResult.Error e   -> throw new IllegalStateException(
                    "Tool '" + toolName + "' failed: " + e.message(), e.cause());
        }
    }

    @Override
    public String configSchema() {
        return """
                {
                  "type": "object",
                  "required": ["tool"],
                  "properties": {
                    "tool": { "type": "string", "description": "Tool name registered in ToolRegistry" },
                    "args": { "type": "string", "description": "JSON object of tool arguments (template-rendered before parse)" }
                  }
                }""";
    }

    private static Map<String, Object> parseArgs(String raw, PipelineContext context) {
        if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
        String rendered = TemplateResolver.resolve(raw, context);
        if (rendered == null || rendered.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = JSON.readValue(rendered, MAP_TYPE);
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Tool Invoke 'args' must parse as a JSON object: " + e.getMessage(), e);
        }
    }
}
