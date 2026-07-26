package io.jaiclaw.pipeline.tool;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.pipeline.render.PipelineRenderService;
import io.jaiclaw.pipeline.render.RenderProfile;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Set;

/**
 * Agent tool {@code pipeline_render} — return an ASCII visualization of
 * one pipeline, ready to paste into a chat channel.
 *
 * <p>Three views (compact/table/flow) and a full set of render profiles
 * (shell_80, slack_desktop, etc.) exposed via inputs. The LLM should
 * pick the shape best-suited to the channel it's speaking to. Prompt
 * hint in the tool description nudges the LLM: {@code compact} for
 * chat channels, {@code table} for dashboards.
 */
public class PipelineRenderTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "pipelineId": {
                  "type": "string",
                  "description": "The pipeline id to render (see pipeline_list)."
                },
                "executionId": {
                  "type": "string",
                  "description": "Optional — a specific execution to render. Omit for the most recent run, or when no run has happened yet."
                },
                "view": {
                  "type": "string",
                  "enum": ["compact", "table", "flow"],
                  "description": "compact = one-liner-per-stage (default, best for chat). table = multi-column status board. flow = top-to-bottom stage-box diagram."
                },
                "profile": {
                  "type": "string",
                  "enum": ["shell_80", "shell_120", "slack_desktop", "slack_mobile", "telegram_desktop", "telegram_mobile", "discord_desktop", "discord_mobile", "email"],
                  "description": "Width profile for the target channel. Defaults to shell_80."
                }
              },
              "required": ["pipelineId"]
            }""";

    private final PipelineRenderService renderService;

    public PipelineRenderTool(PipelineRenderService renderService) {
        super(new ToolDefinition(
                "pipeline_render",
                "Render a JaiClaw pipeline as an ASCII visualization ready to paste into a chat channel. "
                        + "Use view=compact for chat, view=table for a rich status board, view=flow for a "
                        + "top-to-bottom stage diagram.",
                ToolCatalog.SECTION_CUSTOM, INPUT_SCHEMA, Set.of(ToolProfile.FULL)));
        this.renderService = renderService;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> params, ToolContext ctx) throws Exception {
        String pipelineId = requireParam(params, "pipelineId");
        String executionId = optionalParam(params, "executionId", null);
        String viewParam = optionalParam(params, "view", "compact");
        String profileParam = optionalParam(params, "profile", "shell_80");

        PipelineRenderService.View view = PipelineRenderService.View.fromString(viewParam);
        RenderProfile profile = RenderProfile.fromString(profileParam);

        try {
            String rendered = renderService.renderAscii(pipelineId, executionId, view, profile);
            return new ToolResult.Success(rendered);
        } catch (IllegalArgumentException e) {
            return new ToolResult.Error(e.getMessage(), e);
        }
    }
}
