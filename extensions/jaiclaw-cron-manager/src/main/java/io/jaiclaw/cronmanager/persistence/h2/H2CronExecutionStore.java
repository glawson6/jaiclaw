package io.jaiclaw.cronmanager.persistence.h2;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.cronmanager.model.CronExecutionRecord;
import io.jaiclaw.cronmanager.persistence.CronExecutionStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * H2-backed implementation of {@link CronExecutionStore}.
 */
public class H2CronExecutionStore implements CronExecutionStore {

    private final JdbcTemplate jdbc;
    private final TenantGuard tenantGuard;
    private final RowMapper<CronExecutionRecord> rowMapper = new ExecutionRecordRowMapper();

    public H2CronExecutionStore(JdbcTemplate jdbc) {
        this(jdbc, null);
    }

    public H2CronExecutionStore(JdbcTemplate jdbc, TenantGuard tenantGuard) {
        this.jdbc = jdbc;
        this.tenantGuard = tenantGuard;
    }

    private String tenantParam() {
        return tenantGuard != null ? tenantGuard.requireTenantIfMulti() : null;
    }

    @Override
    public void insert(CronExecutionRecord record) {
        jdbc.update("""
                INSERT INTO cron_execution_history (run_id, job_id, job_name, status, result, started_at, completed_at, tenant_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.runId(), record.jobId(), record.jobName(), record.status(),
                record.result(), Timestamp.from(record.startedAt()),
                record.completedAt() != null ? Timestamp.from(record.completedAt()) : null,
                record.tenantId());
    }

    @Override
    public void updateStatus(String runId, String status, String result, Instant completedAt) {
        String tp = tenantParam();
        jdbc.update(
                "UPDATE cron_execution_history SET status = ?, result = ?, completed_at = ? WHERE run_id = ? AND (tenant_id = ? OR ? IS NULL)",
                status, result, completedAt != null ? Timestamp.from(completedAt) : null, runId, tp, tp);
    }

    @Override
    public Optional<CronExecutionRecord> findByRunId(String runId) {
        String tp = tenantParam();
        List<CronExecutionRecord> results = jdbc.query(
                "SELECT * FROM cron_execution_history WHERE run_id = ? AND (tenant_id = ? OR ? IS NULL)",
                rowMapper, runId, tp, tp);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<CronExecutionRecord> findByJobId(String jobId, int limit) {
        String tp = tenantParam();
        return jdbc.query(
                "SELECT * FROM cron_execution_history WHERE job_id = ? AND (tenant_id = ? OR ? IS NULL) ORDER BY started_at DESC LIMIT ?",
                rowMapper, jobId, tp, tp, limit);
    }

    @Override
    public List<CronExecutionRecord> findStartedButNotCompleted() {
        String tp = tenantParam();
        return jdbc.query(
                "SELECT * FROM cron_execution_history WHERE status = 'STARTED' AND completed_at IS NULL AND (tenant_id = ? OR ? IS NULL)",
                rowMapper, tp, tp);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private static class ExecutionRecordRowMapper implements RowMapper<CronExecutionRecord> {
        @Override
        public CronExecutionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CronExecutionRecord(
                    rs.getString("run_id"),
                    rs.getString("job_id"),
                    rs.getString("job_name"),
                    rs.getString("status"),
                    rs.getString("result"),
                    toInstant(rs.getTimestamp("started_at")),
                    toInstant(rs.getTimestamp("completed_at")),
                    rs.getString("tenant_id")
            );
        }
    }
}
