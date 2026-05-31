package io.jaiclaw.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records agent execution trajectories — the sequence of steps (LLM calls,
 * tool invocations, compaction, etc.) that occur during a single agent turn.
 *
 * <p>Each session key maps to a list of {@link TrajectoryStep} instances.
 * Steps are emitted as {@link AuditEvent} records via the {@link AuditLogger}.
 *
 * <p>Usage pattern:
 * <pre>
 *   recorder.beginSession(sessionKey, tenantId);
 *   recorder.recordLlmCall(sessionKey, tenantId, inputSummary, outputSummary, tokens, duration);
 *   recorder.recordToolCall(sessionKey, tenantId, "web_search", inputSummary, outputSummary, duration);
 *   List&lt;TrajectoryStep&gt; trajectory = recorder.endSession(sessionKey);
 * </pre>
 */
public class TrajectoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(TrajectoryRecorder.class);
    private static final int MAX_SUMMARY_LENGTH = 500;

    private final AuditLogger auditLogger;
    private final Map<String, List<TrajectoryStep>> sessions = new ConcurrentHashMap<>();
    private final Map<String, Integer> stepCounters = new ConcurrentHashMap<>();

    public TrajectoryRecorder(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * Begin recording a trajectory for the given session.
     */
    public void beginSession(String sessionKey, String tenantId) {
        sessions.put(sessionKey, Collections.synchronizedList(new ArrayList<>()));
        stepCounters.put(sessionKey, 0);
        log.debug("Trajectory recording started for session: {}", sessionKey);
    }

    /**
     * Record an LLM call step.
     */
    public void recordLlmCall(String sessionKey, String tenantId,
                               String inputSummary, String outputSummary,
                               int tokenCount, Duration duration) {
        recordStep(sessionKey, tenantId, TrajectoryStep.StepType.LLM_CALL,
                null, inputSummary, outputSummary, tokenCount, duration, null);
    }

    /**
     * Record a tool call step.
     */
    public void recordToolCall(String sessionKey, String tenantId,
                                String toolName, String inputSummary, String outputSummary,
                                Duration duration) {
        recordStep(sessionKey, tenantId, TrajectoryStep.StepType.TOOL_CALL,
                toolName, inputSummary, outputSummary, 0, duration, null);
    }

    /**
     * Record a compaction step.
     */
    public void recordCompaction(String sessionKey, String tenantId,
                                  int tokensBefore, int tokensAfter, Duration duration) {
        Map<String, Object> metadata = Map.of(
                "tokensBefore", tokensBefore,
                "tokensAfter", tokensAfter);
        recordStep(sessionKey, tenantId, TrajectoryStep.StepType.COMPACTION,
                null, "tokens=" + tokensBefore, "tokens=" + tokensAfter,
                0, duration, metadata);
    }

    /**
     * Record a memory search step.
     */
    public void recordMemorySearch(String sessionKey, String tenantId,
                                    String query, int resultCount, Duration duration) {
        recordStep(sessionKey, tenantId, TrajectoryStep.StepType.MEMORY_SEARCH,
                null, truncate(query), resultCount + " results", 0, duration, null);
    }

    /**
     * Record a generic step.
     */
    public void recordStep(String sessionKey, String tenantId,
                            TrajectoryStep.StepType stepType, String toolName,
                            String inputSummary, String outputSummary,
                            int tokenCount, Duration duration,
                            Map<String, Object> metadata) {
        int index = stepCounters.merge(sessionKey, 1, Integer::sum) - 1;

        TrajectoryStep step = TrajectoryStep.builder()
                .stepIndex(index)
                .stepType(stepType)
                .toolName(toolName)
                .inputSummary(truncate(inputSummary))
                .outputSummary(truncate(outputSummary))
                .tokenCount(tokenCount)
                .duration(duration)
                .timestamp(Instant.now())
                .metadata(metadata)
                .build();

        List<TrajectoryStep> steps = sessions.get(sessionKey);
        if (steps != null) {
            steps.add(step);
        }

        // Emit as audit event
        AuditEvent event = step.toAuditEvent(tenantId, sessionKey);
        auditLogger.log(event);

        log.debug("Trajectory step [{}] {}: {} ({}ms)",
                index, stepType, toolName != null ? toolName : "-", duration.toMillis());
    }

    /**
     * End recording and return the complete trajectory for the session.
     */
    public List<TrajectoryStep> endSession(String sessionKey) {
        List<TrajectoryStep> steps = sessions.remove(sessionKey);
        stepCounters.remove(sessionKey);
        if (steps == null) {
            return List.of();
        }
        log.debug("Trajectory recording ended for session: {} ({} steps)", sessionKey, steps.size());
        return List.copyOf(steps);
    }

    /**
     * Get the current trajectory steps for an active session (for inspection).
     */
    public List<TrajectoryStep> getSteps(String sessionKey) {
        List<TrajectoryStep> steps = sessions.get(sessionKey);
        return steps != null ? List.copyOf(steps) : List.of();
    }

    /**
     * Check if a session is currently being recorded.
     */
    public boolean isRecording(String sessionKey) {
        return sessions.containsKey(sessionKey);
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_SUMMARY_LENGTH
                ? text.substring(0, MAX_SUMMARY_LENGTH) + "..."
                : text;
    }
}
