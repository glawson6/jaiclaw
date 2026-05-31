package io.jaiclaw.video;

/**
 * Status of an async video generation job.
 */
public enum VideoJobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}
