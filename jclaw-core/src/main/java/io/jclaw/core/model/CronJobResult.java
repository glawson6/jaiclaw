package io.jclaw.core.model;

import java.time.Instant;

/**
 * Result of a cron job execution.
 */
public sealed interface CronJobResult {

    record Success(String jobId, String runId, String agentResponse, Instant completedAt)
            implements CronJobResult {}

    record Failure(String jobId, String runId, String error, Instant failedAt)
            implements CronJobResult {}
}
