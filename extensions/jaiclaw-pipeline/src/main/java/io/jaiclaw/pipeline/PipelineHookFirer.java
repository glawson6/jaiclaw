package io.jaiclaw.pipeline;

import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.plugin.HookRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Fires pipeline lifecycle events through the existing {@link HookRunner} system.
 *
 * <p>Reuses existing {@link HookName} values ({@code BEFORE_TOOL_CALL}, {@code AFTER_TOOL_CALL},
 * {@code BEFORE_AGENT_START}, {@code AGENT_END}) so that plugins like {@code ObservabilityPlugin}
 * automatically capture pipeline stage metrics without code changes.
 *
 * <p>No-ops gracefully when the plugin-sdk module is absent.
 */
public class PipelineHookFirer {

    private static final Logger log = LoggerFactory.getLogger(PipelineHookFirer.class);

    private final HookRunner hookRunner;

    public PipelineHookFirer(HookRunner hookRunner) {
        this.hookRunner = hookRunner;
    }

    /**
     * Fire pipeline start event.
     */
    public void firePipelineStart(PipelineContext ctx) {
        if (hookRunner == null) return;

        Map<String, Object> event = Map.of(
                "pipelineId", ctx.pipelineId(),
                "executionId", ctx.executionId(),
                "totalStages", ctx.totalStages()
        );
        hookRunner.fireVoid(HookName.BEFORE_AGENT_START, event, ctx);
    }

    /**
     * Fire stage start event (as BEFORE_TOOL_CALL for plugin compatibility).
     */
    public void fireStageStart(PipelineContext ctx, StageDefinition stage) {
        if (hookRunner == null) return;

        Map<String, Object> event = Map.of(
                "toolName", stage.name(),
                "pipelineId", ctx.pipelineId(),
                "stageType", stage.type().name(),
                "stageIndex", ctx.stageIndex()
        );
        hookRunner.fireVoid(HookName.BEFORE_TOOL_CALL, event, ctx);
    }

    /**
     * Fire stage completion event (as AFTER_TOOL_CALL for plugin compatibility).
     */
    public void fireStageComplete(PipelineContext ctx, StageDefinition stage, String result) {
        if (hookRunner == null) return;

        Map<String, Object> event = Map.of(
                "toolName", stage.name(),
                "pipelineId", ctx.pipelineId(),
                "stageType", stage.type().name(),
                "resultPreview", result != null ? result.substring(0, Math.min(200, result.length())) : ""
        );
        hookRunner.fireVoid(HookName.AFTER_TOOL_CALL, event, ctx);
    }

    /**
     * Fire pipeline end event.
     */
    public void firePipelineEnd(PipelineContext ctx) {
        if (hookRunner == null) return;

        Map<String, Object> event = Map.of(
                "pipelineId", ctx.pipelineId(),
                "executionId", ctx.executionId(),
                "completedStages", ctx.stageOutputs().size()
        );
        hookRunner.fireVoid(HookName.AGENT_END, event, ctx);
    }
}
