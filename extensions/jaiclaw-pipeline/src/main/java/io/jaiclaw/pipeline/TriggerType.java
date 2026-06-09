package io.jaiclaw.pipeline;

/**
 * How a pipeline execution is initiated.
 */
public enum TriggerType {
    /** File-based trigger (e.g., {@code file://path}). */
    FILE,
    /** Cron expression trigger. */
    CRON,
    /** HTTP endpoint trigger. */
    HTTP,
    /** Arbitrary Camel URI trigger. */
    CAMEL_URI,
    /** Manual (programmatic) trigger only. */
    MANUAL
}
