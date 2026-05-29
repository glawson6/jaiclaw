package io.jaiclaw.tasks;

/**
 * Lifecycle status of a task.
 */
public enum TaskStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    BLOCKED
}
