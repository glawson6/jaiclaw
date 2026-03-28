package io.jaiclaw.cronmanager.model;

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
        Instant completedAt,
        String tenantId
) {
    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    /** Backward-compatible constructor without tenantId. */
    public CronExecutionRecord(String runId, String jobId, String jobName,
                               String status, String result,
                               Instant startedAt, Instant completedAt) {
        this(runId, jobId, jobName, status, result, startedAt, completedAt, null);
    }

    public static CronExecutionRecord started(String runId, String jobId, String jobName) {
        return new CronExecutionRecord(runId, jobId, jobName, STATUS_STARTED, null, Instant.now(), null, null);
    }

    public static CronExecutionRecord started(String runId, String jobId, String jobName, String tenantId) {
        return new CronExecutionRecord(runId, jobId, jobName, STATUS_STARTED, null, Instant.now(), null, tenantId);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String runId;
        private String jobId;
        private String jobName;
        private String status;
        private String result;
        private Instant startedAt;
        private Instant completedAt;
        private String tenantId;

        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder jobId(String jobId) { this.jobId = jobId; return this; }
        public Builder jobName(String jobName) { this.jobName = jobName; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder result(String result) { this.result = result; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }

        public CronExecutionRecord build() {
            return new CronExecutionRecord(runId, jobId, jobName, status, result, startedAt, completedAt, tenantId);
        }
    }
}
