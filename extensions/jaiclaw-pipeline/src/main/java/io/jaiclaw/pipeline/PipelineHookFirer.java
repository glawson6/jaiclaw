package io.jaiclaw.pipeline;

import io.jaiclaw.core.hook.event.AgentEndedEvent;
import io.jaiclaw.core.hook.event.AgentStartedEvent;
import io.jaiclaw.core.hook.event.PipelineExecutionCompletedEvent;
import io.jaiclaw.core.hook.event.PipelineExecutionFailedEvent;
import io.jaiclaw.core.hook.event.PipelineExecutionStartedEvent;
import io.jaiclaw.core.hook.event.PipelineStageCompletedEvent;
import io.jaiclaw.core.hook.event.PipelineStageFailedEvent;
import io.jaiclaw.core.hook.event.PipelineStageStartedEvent;
import io.jaiclaw.core.hook.event.ToolCallEndedEvent;
import io.jaiclaw.core.hook.event.ToolCallStartedEvent;
import io.jaiclaw.plugin.HookRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Fires pipeline lifecycle events through both the {@link HookRunner} plugin
 * system and Spring's {@link ApplicationEventPublisher}.
 *
 * <p><b>Two event streams, both permanent.</b> Every lifecycle transition
 * fires:
 *
 * <ul>
 *   <li><b>Typed pipeline events</b> —
 *       {@link PipelineExecutionStartedEvent},
 *       {@link PipelineStageStartedEvent}, {@link PipelineStageCompletedEvent},
 *       {@link PipelineStageFailedEvent},
 *       {@link PipelineExecutionCompletedEvent},
 *       {@link PipelineExecutionFailedEvent}. UI-shaped consumers (the SSE
 *       endpoint, dashboards, custom pipeline plugins) filter on these
 *       directly without inspecting agent-event conventions.</li>
 *   <li><b>Legacy mapped events</b> —
 *       {@link AgentStartedEvent}, {@link ToolCallStartedEvent},
 *       {@link ToolCallEndedEvent}, {@link AgentEndedEvent}. Each pipeline
 *       execution presents as an "agent" and each stage as a "tool call" so
 *       observability plugins built for agent flows (audit, trajectory
 *       recording, cost telemetry) capture pipeline activity without
 *       modification.</li>
 * </ul>
 *
 * <p>Consumers pick the stream that matches their concern. No deprecation
 * of the mapped events is planned — the two shapes answer genuinely
 * different questions.
 *
 * <p>Typed events are additionally published as Spring
 * {@link org.springframework.context.ApplicationEvent}s so the SSE
 * broadcaster (which subscribes via {@code @EventListener}) fans them out
 * to connected UI clients on the publisher thread rather than through
 * virtual-thread scheduling.
 *
 * <p>The {@code agentId} carried on each mapped event is the pipeline id;
 * the {@code sessionKey} is the execution id. Plugins listening for
 * tool/agent events can distinguish pipeline events by inspecting these
 * fields.
 *
 * <p>No-ops gracefully when the plugin-sdk module is absent
 * ({@code hookRunner == null}). The Spring publisher path is always active.
 */
public class PipelineHookFirer {

    private static final Logger log = LoggerFactory.getLogger(PipelineHookFirer.class);

    /** Max preview length of stage output surfaced on {@link PipelineStageCompletedEvent}. */
    private static final int STAGE_PREVIEW_LEN = 200;

    /** Max failure-reason length surfaced on failure events. */
    private static final int FAILURE_REASON_LEN = 4096;

    private final HookRunner hookRunner;
    private final ApplicationEventPublisher publisher;

    /**
     * Tracks execution start times so we can compute total wall-clock
     * duration on completion / failure without asking the tracker (avoids a
     * hard dep on {@code PipelineExecutionTracker}).
     */
    private final ConcurrentMap<String, Instant> executionStarts = new ConcurrentHashMap<>();

    /**
     * Tracks per-stage start times so we can compute stage duration on
     * completion. Keyed by {@code executionId + ':' + stageIndex}.
     */
    private final ConcurrentMap<String, Instant> stageStarts = new ConcurrentHashMap<>();

    public PipelineHookFirer(HookRunner hookRunner, ApplicationEventPublisher publisher) {
        this.hookRunner = hookRunner;
        this.publisher = publisher;
    }

    /**
     * Legacy convenience constructor for consumers that don't have an
     * {@link ApplicationEventPublisher} bean available. Typed events still
     * fire through the hook runner but nothing publishes them as Spring
     * events, so the SSE broadcaster won't see anything.
     */
    public PipelineHookFirer(HookRunner hookRunner) {
        this(hookRunner, null);
    }

    /**
     * Fire pipeline start events (both typed and legacy mapped).
     */
    public void firePipelineStart(PipelineContext ctx) {
        executionStarts.put(ctx.executionId(), Instant.now());

        // Typed event — for UI consumers + SSE broadcaster + pipeline-aware plugins.
        PipelineExecutionStartedEvent typed = PipelineExecutionStartedEvent.of(
                ctx.pipelineId(), ctx.executionId(), ctx.tenantId());
        fireHook(typed);
        publish(typed);

        // Legacy mapped event — for agent-oriented observability plugins.
        if (hookRunner != null) {
            hookRunner.fireVoid(AgentStartedEvent.of(
                    ctx.pipelineId(), ctx.executionId(), "pipeline-start"));
        }
    }

    /**
     * Fire stage start events (both typed and legacy mapped).
     */
    public void fireStageStart(PipelineContext ctx, StageDefinition stage) {
        stageStarts.put(stageKey(ctx.executionId(), ctx.stageIndex()), Instant.now());

        PipelineStageStartedEvent typed = PipelineStageStartedEvent.of(
                ctx.pipelineId(), ctx.executionId(), ctx.tenantId(),
                stage.name(), stage.type().name(), ctx.stageIndex());
        fireHook(typed);
        publish(typed);

        if (hookRunner != null) {
            hookRunner.fireVoid(ToolCallStartedEvent.of(
                    ctx.pipelineId(), ctx.executionId(),
                    stage.name(), stage.type().name(), ctx.stageIndex()));
        }
    }

    /**
     * Fire stage completion events (both typed and legacy mapped).
     */
    public void fireStageComplete(PipelineContext ctx, StageDefinition stage, String result) {
        String preview = truncate(result, STAGE_PREVIEW_LEN);
        long durationMs = durationMs(stageStarts.remove(stageKey(ctx.executionId(), ctx.stageIndex())));

        PipelineStageCompletedEvent typed = PipelineStageCompletedEvent.of(
                ctx.pipelineId(), ctx.executionId(), ctx.tenantId(),
                stage.name(), stage.type().name(), ctx.stageIndex(),
                preview, durationMs);
        fireHook(typed);
        publish(typed);

        if (hookRunner != null) {
            hookRunner.fireVoid(ToolCallEndedEvent.of(
                    ctx.pipelineId(), ctx.executionId(),
                    stage.name(), stage.type().name(), preview, ctx.stageIndex()));
        }
    }

    /**
     * Fire stage failure events (typed only — no legacy mapping since
     * {@link ToolCallEndedEvent} has no failure discriminator). Called when
     * a single stage throws an unhandled exception. The pipeline's error
     * strategy may still recover — this event does not imply the whole
     * execution failed. Use {@link #firePipelineFailed} for terminal failure.
     */
    public void fireStageFailed(PipelineContext ctx, StageDefinition stage, Throwable failure) {
        String reason = truncate(failure == null ? "unknown" : failure.toString(), FAILURE_REASON_LEN);
        // Drop the stage-start marker (may have been consumed by fireStageComplete already
        // in some race paths — remove() is a no-op then).
        stageStarts.remove(stageKey(ctx.executionId(), ctx.stageIndex()));

        PipelineStageFailedEvent typed = PipelineStageFailedEvent.of(
                ctx.pipelineId(), ctx.executionId(), ctx.tenantId(),
                stage == null ? "unknown" : stage.name(),
                ctx.stageIndex(), reason);
        fireHook(typed);
        publish(typed);
    }

    /**
     * Backward-compat overload of {@link #firePipelineEnd(PipelineContext, String)}
     * with no result — delegates with {@code result = null}.
     */
    public void firePipelineEnd(PipelineContext ctx) {
        firePipelineEnd(ctx, null);
    }

    /**
     * Fire pipeline completion events (both typed and legacy mapped).
     * The legacy {@link AgentEndedEvent#assistantMessage()} is always null
     * on pipeline events — pipelines don't produce an assistant message.
     *
     * @param ctx    the pipeline execution context
     * @param result caller-visible result string resolved by the runtime;
     *               nullable when neither a stage bean nor the definition's
     *               {@code resultTemplate} produced one. See
     *               {@link io.jaiclaw.pipeline.PipelineResult}.
     */
    public void firePipelineEnd(PipelineContext ctx, String result) {
        long durationMs = durationMs(executionStarts.remove(ctx.executionId()));

        PipelineExecutionCompletedEvent typed = PipelineExecutionCompletedEvent.of(
                ctx.pipelineId(), ctx.executionId(), ctx.tenantId(),
                ctx.totalStages(), durationMs, result);
        fireHook(typed);
        publish(typed);

        if (hookRunner != null) {
            hookRunner.fireVoid(AgentEndedEvent.of(
                    ctx.pipelineId(), ctx.executionId(), null));
        }
    }

    /**
     * Fire pipeline failure events (typed only — no legacy mapping since
     * {@link AgentEndedEvent} has no failure discriminator). Called when
     * the pipeline terminates due to an unrecovered exception.
     *
     * @param ctx          the pipeline execution context
     * @param failedStage  name of the stage where the failure originated;
     *                     {@code null} for framework-level faults
     * @param failure      the throwable that terminated the pipeline
     */
    public void firePipelineFailed(PipelineContext ctx, String failedStage, Throwable failure) {
        String reason = truncate(failure == null ? "unknown" : failure.toString(), FAILURE_REASON_LEN);
        long durationMs = durationMs(executionStarts.remove(ctx.executionId()));

        PipelineExecutionFailedEvent typed = PipelineExecutionFailedEvent.of(
                ctx.pipelineId(), ctx.executionId(), ctx.tenantId(),
                failedStage, reason, durationMs);
        fireHook(typed);
        publish(typed);
    }

    // --- helpers ---

    private void fireHook(io.jaiclaw.core.hook.event.HookEvent event) {
        if (hookRunner == null) return;
        hookRunner.fireVoid(event);
    }

    private void publish(Object event) {
        if (publisher == null) return;
        try {
            publisher.publishEvent(event);
        } catch (Exception e) {
            // Never break the pipeline execution because a listener failed.
            log.warn("Pipeline event publish failed for {}: {}",
                    event.getClass().getSimpleName(), e.toString());
        }
    }

    private static String stageKey(String executionId, int stageIndex) {
        return executionId + ':' + stageIndex;
    }

    private static long durationMs(Instant start) {
        if (start == null) return 0L;
        return Duration.between(start, Instant.now()).toMillis();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
