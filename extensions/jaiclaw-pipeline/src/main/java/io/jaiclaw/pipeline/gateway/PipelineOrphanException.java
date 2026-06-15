package io.jaiclaw.pipeline.gateway;

/**
 * Thrown (via {@link java.util.concurrent.CompletableFuture#completeExceptionally})
 * by {@link PipelineSyncCoordinator}'s orphan sweep when a pending synchronous
 * execution exceeds {@code jaiclaw.pipeline.sync.orphan-ttl} without being
 * completed by the route. Bounds the leak when the pipeline route fails to
 * complete the future (JVM crash mid-execution, route-builder bug, exchange
 * dropped before reaching the output / onException handler).
 */
public class PipelineOrphanException extends RuntimeException {

    private final String executionId;

    public PipelineOrphanException(String executionId, String message) {
        super(message);
        this.executionId = executionId;
    }

    /** UUID of the orphaned execution. */
    public String executionId() {
        return executionId;
    }
}
