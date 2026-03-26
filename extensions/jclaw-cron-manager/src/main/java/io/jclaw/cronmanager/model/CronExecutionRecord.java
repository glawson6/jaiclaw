package io.jclaw.cronmanager.model;

import java.time.Instant;

/**
 * Persistent record of a single cron job execution.
 *
 * @param runId       unique execution identifier
 * @param jobId       the cron job that was executed
 * @param jobName     human-readable job name (denormalized for history queries)
 * @param status      execution status: STARTED, COMPLETED, or FAILED
 * @param result      agent response text or error message
 * @param startedAt   when execution began
 * @param completedAt when execution finished (null if still running)
 */
public record CronExecutionRecord(
        String runId,
        String jobId,
        String jobName,
        String status,
        String result,
        Instant startedAt,
        Instant completedAt
) {
    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static CronExecutionRecord started(String runId, String jobId, String jobName) {
        return new CronExecutionRecord(runId, jobId, jobName, STATUS_STARTED, null, Instant.now(), null);
    }
}
