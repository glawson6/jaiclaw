package io.jaiclaw.pipeline.gateway;

/**
 * Wrapped in an already-failed {@link java.util.concurrent.CompletableFuture}
 * by {@link PipelineSyncCoordinator#register(String)} when the in-flight
 * synchronous-execution count exceeds {@code jaiclaw.pipeline.sync.max-pending}.
 *
 * <p>Callers using {@link PipelineGateway#triggerAsync(String, String)} see
 * this surfaced as the cause of an {@code ExecutionException} from the returned
 * future; {@link PipelineGateway#triggerAndAwait(String, String, java.time.Duration)}
 * unwraps and propagates the cause.
 */
public class PipelineCapacityException extends RuntimeException {

    private final String executionId;
    private final int capacity;

    public PipelineCapacityException(String executionId, int capacity) {
        super("Pipeline sync coordinator at capacity (" + capacity
                + " pending) — rejected executionId=" + executionId);
        this.executionId = executionId;
        this.capacity = capacity;
    }

    /** UUID of the rejected execution. */
    public String executionId() {
        return executionId;
    }

    /** Configured capacity at the time of rejection. */
    public int capacity() {
        return capacity;
    }
}
