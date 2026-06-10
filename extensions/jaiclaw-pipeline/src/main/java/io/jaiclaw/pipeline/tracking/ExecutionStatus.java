package io.jaiclaw.pipeline.tracking;

/**
 * Lifecycle state of a tracked pipeline execution.
 */
public enum ExecutionStatus {
    /** The execution is currently in flight. */
    RUNNING,
    /** The execution completed successfully. */
    SUCCESS,
    /** The execution failed (see {@link PipelineExecutionSummary#failureReason()}). */
    FAILED
}
