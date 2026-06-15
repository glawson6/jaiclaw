package io.jaiclaw.pipeline.gateway;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Programmatic entrypoint for triggering pipelines by ID. Hides the internal
 * Camel {@code direct:pipeline-<id>} URI scheme so callers don't depend on
 * route-naming implementation details.
 *
 * <p>Two flavors:
 * <ul>
 *   <li><b>Fire-and-forget</b> — {@link #trigger(String, String)} (and overloads).
 *       Returns a {@link PipelineExecutionHandle} immediately; callers must
 *       poll the actuator endpoint to see results.</li>
 *   <li><b>Synchronous</b> — {@link #triggerAsync(String, String)} returns a
 *       {@link CompletableFuture} that completes with a
 *       {@link PipelineExecutionResult} at the end of the pipeline (success
 *       <i>and</i> stage-failure both complete normally; only true timeouts and
 *       infrastructure faults complete exceptionally).
 *       {@link #triggerAndAwait(String, String, Duration)} is the blocking
 *       convenience.</li>
 * </ul>
 */
public interface PipelineGateway {

    /** Trigger a pipeline with the given input body (fire-and-forget). */
    PipelineExecutionHandle trigger(String pipelineId, String input);

    /** Trigger a pipeline with input and an explicit tenant ID (fire-and-forget). */
    PipelineExecutionHandle trigger(String pipelineId, String input, String tenantId);

    /** Trigger a pipeline with input, tenant ID, and correlation ID (fire-and-forget). */
    PipelineExecutionHandle trigger(String pipelineId, String input, String tenantId, String correlationId);

    /**
     * Trigger a pipeline and return a {@link CompletableFuture} that completes
     * with the {@link PipelineExecutionResult} when the pipeline finishes.
     * Stage failures complete the future normally with
     * {@code status=FAILED}; only infrastructure faults (coordinator
     * capacity, orphan reaping, unknown pipeline) complete exceptionally.
     */
    CompletableFuture<PipelineExecutionResult> triggerAsync(String pipelineId, String input);

    /** Async-trigger variant with an explicit tenant ID. */
    CompletableFuture<PipelineExecutionResult> triggerAsync(String pipelineId, String input, String tenantId);

    /** Async-trigger variant with tenant and correlation IDs. */
    CompletableFuture<PipelineExecutionResult> triggerAsync(
            String pipelineId, String input, String tenantId, String correlationId);

    /**
     * Blocking convenience that awaits the {@link #triggerAsync(String, String)}
     * future up to {@code timeout}. On timeout, throws {@link TimeoutException}
     * — the pipeline keeps running in the background and the result
     * eventually lands in the {@code PipelineExecutionTracker}.
     *
     * @throws TimeoutException     if the pipeline doesn't finish before the timeout
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws ExecutionException   if the pipeline completes exceptionally
     *                              (e.g. {@link PipelineCapacityException},
     *                              {@link PipelineOrphanException}) — stage
     *                              failures do NOT throw, they return a result
     *                              with {@code status=FAILED}
     */
    default PipelineExecutionResult triggerAndAwait(String pipelineId, String input, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        return triggerAndAwait(pipelineId, input, null, null, timeout);
    }

    /** Sync variant with explicit tenant id. */
    default PipelineExecutionResult triggerAndAwait(
            String pipelineId, String input, String tenantId, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        return triggerAndAwait(pipelineId, input, tenantId, null, timeout);
    }

    /** Sync variant with explicit tenant and correlation ids. */
    default PipelineExecutionResult triggerAndAwait(
            String pipelineId, String input, String tenantId, String correlationId, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return triggerAsync(pipelineId, input, tenantId, correlationId)
                .get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
}
