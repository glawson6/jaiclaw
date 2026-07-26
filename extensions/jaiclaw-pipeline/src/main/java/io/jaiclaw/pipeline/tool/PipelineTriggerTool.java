package io.jaiclaw.pipeline.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle;
import io.jaiclaw.pipeline.gateway.PipelineExecutionResult;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Agent tool {@code pipeline_trigger} — starts a pipeline run.
 *
 * <p>Two modes controlled by {@code awaitSeconds}:
 * <ul>
 *   <li>{@code awaitSeconds == 0} (default) — fire-and-forget. Returns
 *       the {@link PipelineExecutionHandle} JSON. The agent can then poll
 *       via {@code pipeline_status}.</li>
 *   <li>{@code awaitSeconds > 0} — synchronous. Blocks up to that many
 *       seconds and returns the full {@link PipelineExecutionResult}
 *       (including per-stage outputs + status).</li>
 * </ul>
 *
 * <p>Tenant context is inherited from the current
 * {@link ToolContext#agentId()} when not overridden explicitly.
 */
public class PipelineTriggerTool extends AbstractBuiltinTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "pipelineId": {
                  "type": "string",
                  "description": "The pipeline definition id to trigger."
                },
                "input": {
                  "type": "string",
                  "description": "Payload handed to the pipeline's first stage (referenced as {{input}} in stage templates). Empty string when omitted."
                },
                "tenantId": {
                  "type": "string",
                  "description": "Tenant to execute under. Omit in single-tenant deployments."
                },
                "correlationId": {
                  "type": "string",
                  "description": "Optional distributed-tracing correlation id threaded through the pipeline context."
                },
                "awaitSeconds": {
                  "type": "integer",
                  "description": "0 (default) for fire-and-forget; positive N to block up to N seconds for the full execution result.",
                  "minimum": 0
                }
              },
              "required": ["pipelineId"]
            }""";

    private final PipelineGateway gateway;

    public PipelineTriggerTool(PipelineGateway gateway) {
        super(new ToolDefinition(
                "pipeline_trigger",
                "Trigger a JaiClaw pipeline. Returns a handle immediately (fire-and-forget) or the completed execution result when awaitSeconds > 0.",
                ToolCatalog.SECTION_CUSTOM, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.gateway = gateway;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext ctx) throws Exception {
        String pipelineId = requireParam(params, "pipelineId");
        String input = optionalParam(params, "input", "");
        String tenantId = optionalParam(params, "tenantId", null);
        String correlationId = optionalParam(params, "correlationId", null);
        int awaitSeconds = parseIntOrDefault(params.get("awaitSeconds"), 0);

        if (awaitSeconds > 0) {
            PipelineExecutionResult result = gateway.triggerAndAwait(
                    pipelineId, input, tenantId, correlationId,
                    Duration.ofSeconds(awaitSeconds));
            return new ToolResult.Success(JSON.writeValueAsString(result));
        }
        PipelineExecutionHandle handle;
        if (correlationId != null) {
            handle = gateway.trigger(pipelineId, input, tenantId, correlationId);
        } else if (tenantId != null) {
            handle = gateway.trigger(pipelineId, input, tenantId);
        } else {
            handle = gateway.trigger(pipelineId, input);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("executionId", handle.executionId());
        body.put("pipelineId", handle.pipelineId());
        body.put("submittedAt", handle.submittedAt().toString());
        body.put("status", "SUBMITTED");
        return new ToolResult.Success(JSON.writeValueAsString(body));
    }

    private static int parseIntOrDefault(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
