package io.jaiclaw.pipeline.render;

import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.StageType;
import io.jaiclaw.pipeline.tracking.ExecutionStatus;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable joined snapshot of a {@link PipelineDefinition} and (optionally)
 * one of its {@link PipelineExecutionSummary}s. Consumed by both the
 * ASCII and HTML renderers so their per-stage view is derived from the same
 * source of truth.
 *
 * <p>Construct via
 * {@link #of(PipelineDefinition, PipelineExecutionSummary)}. Pass
 * {@code summary=null} for the "no execution yet" case (all stages
 * become {@link StageStatus#PENDING}, overall status becomes
 * {@code null}).
 */
public record PipelineViewModel(
        String pipelineId,
        String pipelineName,
        String pipelineDescription,
        String executionId,
        String tenantId,
        ExecutionStatus overallStatus,
        Duration totalDuration,
        String failureReason,
        List<Stage> stages
) {

    public PipelineViewModel {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    /**
     * Joined per-stage view. Every field is nullable-safe; renderers should
     * handle nulls (missing name shouldn't happen but empty {@code durationMs}
     * is normal for PENDING/RUNNING stages).
     */
    public record Stage(
            String name,
            StageType type,
            StageStatus status,
            Long durationMs,
            String outputPreview,
            String failureReason,
            String beanOrUri,
            int index
    ) {

        /** Human-readable stage type name (e.g. {@code AGENT}, {@code PROCESSOR}). */
        public String typeLabel() {
            return type == null ? "" : type.name();
        }

        /**
         * Duration formatted for the compact view: {@code "3.4s"} or
         * {@code "42ms"} or {@code "—"} when null.
         */
        public String durationLabel() {
            if (durationMs == null) return "—";
            if (durationMs < 1000) return durationMs + "ms";
            double secs = durationMs / 1000.0;
            return String.format(java.util.Locale.ROOT, "%.1fs", secs);
        }
    }

    /**
     * Build a view model by joining a pipeline definition with an
     * optional execution summary.
     *
     * <p>Per-stage status resolution:
     * <ul>
     *   <li>{@code summary == null} → every stage {@link StageStatus#PENDING},
     *       overall status null.</li>
     *   <li>Stage present in {@code summary.stageDurations()} → {@link StageStatus#DONE}.</li>
     *   <li>Stage matches {@code summary.currentStage()} → {@link StageStatus#RUNNING}
     *       (or {@link StageStatus#FAILED} if the overall status is FAILED and
     *       this is the last-known active stage).</li>
     *   <li>Otherwise → {@link StageStatus#PENDING}.</li>
     * </ul>
     */
    public static PipelineViewModel of(PipelineDefinition definition, PipelineExecutionSummary summary) {
        Objects.requireNonNull(definition, "definition");
        List<StageDefinition> stageDefs = definition.stages() == null ? List.of() : definition.stages();
        Map<String, Duration> durations = summary == null || summary.stageDurations() == null
                ? Map.of()
                : summary.stageDurations();
        String currentStage = summary == null ? null : summary.currentStage();
        ExecutionStatus overall = summary == null ? null : summary.status();

        List<Stage> stages = new ArrayList<>(stageDefs.size());
        for (int i = 0; i < stageDefs.size(); i++) {
            StageDefinition sd = stageDefs.get(i);
            StageStatus status = resolveStatus(sd.name(), durations, currentStage, overall);
            Duration dur = durations.get(sd.name());
            Long durMs = dur == null ? null : dur.toMillis();
            // outputPreview + failureReason live on the events, not on the
            // summary — the summary only tracks per-stage timings. We surface
            // stage-level failureReason only when the summary's overall
            // failureReason belongs to this stage (the last active one).
            String failReason = null;
            if (status == StageStatus.FAILED && summary != null) {
                failReason = summary.failureReason();
            }
            stages.add(new Stage(
                    sd.name(),
                    sd.type(),
                    status,
                    durMs,
                    null,
                    failReason,
                    resolveBeanOrUri(sd),
                    i));
        }

        return new PipelineViewModel(
                definition.id(),
                definition.name(),
                definition.description(),
                summary == null ? null : summary.executionId(),
                summary == null ? null : summary.tenantId(),
                overall,
                summary == null ? null : summary.totalDuration(),
                summary == null ? null : summary.failureReason(),
                stages);
    }

    private static StageStatus resolveStatus(String name, Map<String, Duration> durations,
                                             String currentStage, ExecutionStatus overall) {
        if (durations.containsKey(name)) {
            return StageStatus.DONE;
        }
        if (name.equals(currentStage)) {
            return overall == ExecutionStatus.FAILED ? StageStatus.FAILED : StageStatus.RUNNING;
        }
        // The FAILED overall status may not have a currentStage marker (some
        // execution paths clear it). Fall back: the first non-DONE stage in
        // a FAILED execution is treated as the failure point — but we can't
        // tell here without full history. Leave it PENDING; the caller can
        // enrich if needed.
        return StageStatus.PENDING;
    }

    private static String resolveBeanOrUri(StageDefinition sd) {
        if (sd.bean() != null && !sd.bean().isBlank()) {
            return "bean:" + sd.bean();
        }
        if (sd.uri() != null && !sd.uri().isBlank()) {
            return sd.uri();
        }
        if (sd.agentId() != null && !sd.agentId().isBlank()) {
            return "agent:" + sd.agentId();
        }
        return "";
    }

    /**
     * Total number of stages (matches {@code stages().size()}).
     */
    public int totalStages() {
        return stages.size();
    }

    /**
     * Index (1-based) of the currently-running stage, or 0 if none is
     * running. Convenience for renderers that show {@code "stage 3/5"}.
     */
    public int currentStageIndex1Based() {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).status() == StageStatus.RUNNING) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Total duration formatted for the compact/table header:
     * {@code "8.7s"} or {@code "—"} when null. Matches the format used
     * by {@link Stage#durationLabel()}.
     */
    public String totalDurationLabel() {
        if (totalDuration == null) return "—";
        long ms = totalDuration.toMillis();
        if (ms < 1000) return ms + "ms";
        return String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0);
    }

    /**
     * Short 6-char execution id fragment for compact display headers.
     * Returns an empty string when there is no executionId.
     */
    public String shortExecutionId() {
        if (executionId == null || executionId.isEmpty()) return "";
        int len = Math.min(6, executionId.length());
        return executionId.substring(0, len);
    }
}
