package io.jaiclaw.pipeline.tracking;

import io.jaiclaw.pipeline.PipelineContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, bounded in-memory store of recent pipeline executions. Used by
 * the {@code PipelineActuatorEndpoint} so operators can see what ran when.
 *
 * <p>Per pipeline: keeps the last {@code maxPerPipeline} executions in an
 * {@link ArrayDeque} guarded by {@code synchronized(deque)}. A separate map
 * indexed by executionId allows direct lookup. Old entries are evicted
 * (oldest-first) inside the same synchronized block so both views stay in sync.
 */
public class PipelineExecutionTracker {

    /** Default upper bound on tracked executions per pipeline. */
    public static final int DEFAULT_MAX_PER_PIPELINE = 50;

    private final int maxPerPipeline;
    private final ConcurrentHashMap<String, Deque<PipelineExecutionSummary>> byPipeline = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PipelineExecutionSummary> byExecutionId = new ConcurrentHashMap<>();

    public PipelineExecutionTracker() {
        this(DEFAULT_MAX_PER_PIPELINE);
    }

    public PipelineExecutionTracker(int maxPerPipeline) {
        this.maxPerPipeline = maxPerPipeline <= 0 ? DEFAULT_MAX_PER_PIPELINE : maxPerPipeline;
    }

    /** Record that a pipeline execution has started. */
    public void started(PipelineContext ctx) {
        if (ctx == null) return;
        PipelineExecutionSummary summary = new PipelineExecutionSummary(
                ctx.executionId(), ctx.pipelineId(), ctx.tenantId(),
                Instant.now(), null, ExecutionStatus.RUNNING, null,
                Map.of(), null, null);
        insert(summary);
    }

    /** Mark that the named stage has started executing. */
    public void stageStarted(PipelineContext ctx, String stageName) {
        if (ctx == null) return;
        update(ctx.executionId(), s -> s.withCurrentStage(stageName));
    }

    /** Record completion of a stage with the elapsed duration. */
    public void stageCompleted(PipelineContext ctx, String stageName, Duration duration) {
        if (ctx == null) return;
        update(ctx.executionId(), s -> s.withStageDuration(stageName, duration));
    }

    /** Mark the execution as successfully complete. */
    public void succeeded(PipelineContext ctx, Duration totalDuration) {
        if (ctx == null) return;
        update(ctx.executionId(), s -> s.completedSuccessfully(Instant.now(), totalDuration));
    }

    /** Mark the execution as failed with the given reason. */
    public void failed(PipelineContext ctx, String reason, Duration totalDuration) {
        if (ctx == null) return;
        update(ctx.executionId(), s -> s.completedWithFailure(Instant.now(), reason, totalDuration));
    }

    /**
     * Return recent executions for the given pipeline, oldest-first. Snapshot
     * is taken under the deque lock so iteration is safe.
     */
    public List<PipelineExecutionSummary> recent(String pipelineId) {
        Deque<PipelineExecutionSummary> deque = byPipeline.get(pipelineId);
        if (deque == null) return List.of();
        synchronized (deque) {
            return List.copyOf(deque);
        }
    }

    /** Look up an execution by its UUID. */
    public Optional<PipelineExecutionSummary> byId(String executionId) {
        if (executionId == null) return Optional.empty();
        return Optional.ofNullable(byExecutionId.get(executionId));
    }

    /** Return all pipeline IDs that have tracked executions. */
    public List<String> pipelineIds() {
        return new ArrayList<>(byPipeline.keySet());
    }

    /** Total number of tracked execution records across all pipelines. */
    public int size() {
        return byExecutionId.size();
    }

    /** Drop all tracked state. */
    public void clear() {
        byPipeline.clear();
        byExecutionId.clear();
    }

    private void insert(PipelineExecutionSummary summary) {
        Deque<PipelineExecutionSummary> deque = byPipeline.computeIfAbsent(
                summary.pipelineId(), k -> new ArrayDeque<>());
        synchronized (deque) {
            while (deque.size() >= maxPerPipeline) {
                PipelineExecutionSummary evicted = deque.pollFirst();
                if (evicted != null) {
                    byExecutionId.remove(evicted.executionId());
                }
            }
            deque.addLast(summary);
            byExecutionId.put(summary.executionId(), summary);
        }
    }

    private void update(String executionId, java.util.function.UnaryOperator<PipelineExecutionSummary> fn) {
        if (executionId == null) return;
        PipelineExecutionSummary existing = byExecutionId.get(executionId);
        if (existing == null) return;
        PipelineExecutionSummary updated = fn.apply(existing);
        if (updated == null || updated == existing) return;
        Deque<PipelineExecutionSummary> deque = byPipeline.get(existing.pipelineId());
        if (deque == null) return;
        synchronized (deque) {
            // Replace in-place to preserve insertion order without an iterator.
            int size = deque.size();
            boolean replaced = false;
            for (int i = 0; i < size; i++) {
                PipelineExecutionSummary head = deque.pollFirst();
                if (head == null) break;
                if (!replaced && executionId.equals(head.executionId())) {
                    deque.addLast(updated);
                    replaced = true;
                } else {
                    deque.addLast(head);
                }
            }
            if (replaced) {
                byExecutionId.put(executionId, updated);
            }
        }
    }

    /** Read-only view for diagnostics / tests. */
    public Map<String, Integer> sizesByPipeline() {
        Map<String, Integer> result = new java.util.HashMap<>();
        for (Map.Entry<String, Deque<PipelineExecutionSummary>> entry : byPipeline.entrySet()) {
            synchronized (entry.getValue()) {
                result.put(entry.getKey(), entry.getValue().size());
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
