package io.jaiclaw.pipeline.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent tool {@code pipeline_status} — inspect one execution by its
 * {@code executionId}. Delegates to {@link PipelineExecutionTracker}'s
 * bounded in-memory ring buffer; returns 404-shaped error when the id
 * has fallen out of the buffer or was never valid.
 */
public class PipelineStatusTool extends AbstractBuiltinTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "executionId": {
                  "type": "string",
                  "description": "The executionId returned by pipeline_trigger."
                },
                "pipelineId": {
                  "type": "string",
                  "description": "Optional — narrows the tracker lookup when specified. Otherwise the tracker scans all pipelines."
                }
              },
              "required": ["executionId"]
            }""";

    private final PipelineExecutionTracker tracker;

    public PipelineStatusTool(PipelineExecutionTracker tracker) {
        super(new ToolDefinition(
                "pipeline_status",
                "Fetch the status of a single pipeline execution by its id.",
                ToolCatalog.SECTION_CUSTOM, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.tracker = tracker;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext ctx) throws Exception {
        String executionId = requireParam(params, "executionId");
        Optional<PipelineExecutionSummary> found = tracker.byId(executionId);
        if (found.isEmpty()) {
            return new ToolResult.Error(
                    "Execution not found: " + executionId
                            + " (may have fallen out of the tracker's bounded history — "
                            + "check /actuator/pipelines/{id} for a broader view)",
                    null);
        }
        return new ToolResult.Success(JSON.writeValueAsString(found.get()));
    }
}
