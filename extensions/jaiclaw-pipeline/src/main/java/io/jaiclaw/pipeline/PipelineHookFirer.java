package io.jaiclaw.pipeline;

import io.jaiclaw.core.hook.event.AgentEndedEvent;
import io.jaiclaw.core.hook.event.AgentStartedEvent;
import io.jaiclaw.core.hook.event.ToolCallEndedEvent;
import io.jaiclaw.core.hook.event.ToolCallStartedEvent;
import io.jaiclaw.plugin.HookRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fires pipeline lifecycle events through the existing {@link HookRunner} system.
 *
 * <p>0.8.0 hard-break: now emits typed
 * {@link io.jaiclaw.core.hook.event.HookEvent} subtypes
 * ({@link AgentStartedEvent}, {@link ToolCallStartedEvent},
 * {@link ToolCallEndedEvent}, {@link AgentEndedEvent}) — pipeline stages
 * are reported as tool calls so existing observability plugins capture
 * pipeline stage metrics without changes.
 *
 * <p>The {@code agentId} carried on each event is the pipeline id; the
 * {@code sessionKey} is the execution id. Plugins listening for tool/agent
 * events can distinguish pipeline events by inspecting these fields.
 *
 * <p>No-ops gracefully when the plugin-sdk module is absent
 * ({@code hookRunner == null}).
 */
public class PipelineHookFirer {

    private static final Logger log = LoggerFactory.getLogger(PipelineHookFirer.class);

    private final HookRunner hookRunner;

    public PipelineHookFirer(HookRunner hookRunner) {
        this.hookRunner = hookRunner;
    }

    /**
     * Fire pipeline start event (as {@link AgentStartedEvent}).
     */
    public void firePipelineStart(PipelineContext ctx) {
        if (hookRunner == null) return;
        hookRunner.fireVoid(AgentStartedEvent.of(
                ctx.pipelineId(), ctx.executionId(), "pipeline-start"));
    }

    /**
     * Fire stage start event (as {@link ToolCallStartedEvent} for plugin compatibility).
     */
    public void fireStageStart(PipelineContext ctx, StageDefinition stage) {
        if (hookRunner == null) return;
        hookRunner.fireVoid(ToolCallStartedEvent.of(
                ctx.pipelineId(), ctx.executionId(),
                stage.name(), stage.type().name(), ctx.stageIndex()));
    }

    /**
     * Fire stage completion event (as {@link ToolCallEndedEvent}).
     */
    public void fireStageComplete(PipelineContext ctx, StageDefinition stage, String result) {
        if (hookRunner == null) return;
        String preview = result != null ? result.substring(0, Math.min(200, result.length())) : "";
        hookRunner.fireVoid(ToolCallEndedEvent.of(
                ctx.pipelineId(), ctx.executionId(),
                stage.name(), stage.type().name(), preview, ctx.stageIndex()));
    }

    /**
     * Fire pipeline end event (as {@link AgentEndedEvent}).
     *
     * <p>The carried {@link AgentEndedEvent#assistantMessage()} is null on
     * pipeline events — pipelines don't produce a {@code AssistantMessage}.
     */
    public void firePipelineEnd(PipelineContext ctx) {
        if (hookRunner == null) return;
        hookRunner.fireVoid(AgentEndedEvent.of(
                ctx.pipelineId(), ctx.executionId(), null));
    }
}
