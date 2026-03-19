package io.jclaw.cronmanager.persistence;

import io.jclaw.cronmanager.model.CronExecutionRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction for cron execution history.
 * Implementations may use H2, MySQL, Redis, etc.
 */
public interface CronExecutionStore {

    void insert(CronExecutionRecord record);

    void updateStatus(String runId, String status, String result, Instant completedAt);

    Optional<CronExecutionRecord> findByRunId(String runId);

    List<CronExecutionRecord> findByJobId(String jobId, int limit);

    List<CronExecutionRecord> findStartedButNotCompleted();
}
