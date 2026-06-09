package io.jaiclaw.pipeline;

/**
 * Strategy for handling stage failures within a pipeline.
 */
public enum ErrorStrategy {
    /** Route failed messages to a dead-letter queue. */
    DEAD_LETTER,
    /** Retry the stage up to {@code maxRetries}, then fail the pipeline. */
    RETRY_THEN_FAIL,
    /** Stop the pipeline immediately on failure. */
    STOP
}
