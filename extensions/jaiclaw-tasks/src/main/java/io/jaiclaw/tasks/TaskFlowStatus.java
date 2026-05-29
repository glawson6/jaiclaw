package io.jaiclaw.tasks;

/**
 * Lifecycle status of a task flow (multi-step pipeline).
 */
public enum TaskFlowStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
