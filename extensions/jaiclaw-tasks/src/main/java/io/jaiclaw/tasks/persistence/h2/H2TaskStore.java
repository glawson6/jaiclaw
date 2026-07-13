package io.jaiclaw.tasks.persistence.h2;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.tasks.TaskDeliveryState;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStatus;
import io.jaiclaw.tasks.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * H2-backed {@link TaskStore} implementation. Plan §9 Phase 4 / analysis
 * §6.5: enables multi-instance deployments, transactional WIP checks,
 * and lease-based recovery.
 *
 * <p>Schema lives in {@code src/main/resources/schema.sql} and is loaded
 * automatically by Spring Boot's {@code spring.sql.init} (cron-manager
 * precedent).
 *
 * <p>{@code compareAndSave} uses the canonical row-count CAS:
 * {@code UPDATE ... WHERE id = ? AND version = ?}. Two concurrent writers
 * with the same expected version race the database; the loser's update
 * affects zero rows and we return {@link Optional#empty}.
 *
 * <p>Lease columns ({@code claimed_by} + {@code lease_until}) are
 * managed by {@link #claim} / {@link #renewLease} / {@link #releaseLease}
 * for the multi-instance recovery story.
 */
public class H2TaskStore implements TaskStore {

    private static final Logger log = LoggerFactory.getLogger(H2TaskStore.class);
    private static final TypeReference<Map<String, String>> METADATA_TYPE =
            new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final TenantGuard tenantGuard;
    private final ObjectMapper json = new ObjectMapper();
    private final RowMapper<TaskRecord> rowMapper = new TaskRowMapper();

    public H2TaskStore(JdbcTemplate jdbc) {
        this(jdbc, new TenantGuard(TenantProperties.DEFAULT));
    }

    public H2TaskStore(JdbcTemplate jdbc, TenantGuard tenantGuard) {
        this.jdbc = jdbc;
        this.tenantGuard = tenantGuard != null ? tenantGuard
                : new TenantGuard(TenantProperties.DEFAULT);
    }

    @Override
    public void save(TaskRecord task) {
        String tenantId = effectiveTenantId(task);
        // MERGE KEY needs to include tenant_id so two tenants can share an
        // id without one's save clobbering the other (the primary key
        // remains id-only — H2 enforces global uniqueness — but we make
        // upserts tenant-scoped by branching on existence).
        Optional<TaskRecord> existing = findInTenant(task.id(), tenantId);
        if (existing.isPresent()) {
            jdbc.update("""
                    UPDATE jaiclaw_tasks
                       SET name = ?, description = ?, status = ?, delivery_state = ?,
                           result = ?, error = ?, flow_id = ?, metadata_json = ?,
                           created_at = ?, started_at = ?, completed_at = ?,
                           board_id = ?, state = ?, assignee = ?, version = ?,
                           order_index = ?, idempotency_key = ?
                     WHERE id = ? AND tenant_id = ?
                    """,
                    task.name(), task.description(),
                    task.status().name(), task.deliveryState().name(),
                    task.result(), task.error(), task.flowId(), writeMetadata(task.metadata()),
                    toTimestamp(task.createdAt()), toTimestamp(task.startedAt()),
                    toTimestamp(task.completedAt()),
                    task.boardId(), task.state(), task.assignee(), task.version(),
                    task.orderIndex(), task.idempotencyKey(),
                    task.id(), tenantId);
        } else {
            insert(task, tenantId);
        }
    }

    @Override
    public synchronized Optional<TaskRecord> compareAndSave(TaskRecord task) {
        String tenantId = effectiveTenantId(task);
        long expected = task.version();
        long next = expected + 1;

        // First, INSERT-or-CAS-UPDATE — we can't MERGE under a version guard,
        // so we branch on whether a row already exists.
        Optional<TaskRecord> existing = findInTenant(task.id(), tenantId);
        if (existing.isEmpty()) {
            // Brand-new row: insert with version=expected+1 so the next CAS
            // call sees v=1 against expected=0/1. Matches JsonFileTaskStore's
            // contract.
            TaskRecord persisted = task.withVersion(next);
            insert(persisted, tenantId);
            return Optional.of(persisted);
        }
        if (existing.get().version() != expected) {
            return Optional.empty();
        }
        int updated = jdbc.update("""
                UPDATE jaiclaw_tasks
                   SET name = ?, description = ?, status = ?, delivery_state = ?,
                       result = ?, error = ?, flow_id = ?, metadata_json = ?,
                       created_at = ?, started_at = ?, completed_at = ?,
                       board_id = ?, state = ?, assignee = ?, order_index = ?,
                       idempotency_key = ?,
                       version = ?
                 WHERE id = ? AND tenant_id = ? AND version = ?
                """,
                task.name(), task.description(),
                task.status().name(), task.deliveryState().name(),
                task.result(), task.error(), task.flowId(), writeMetadata(task.metadata()),
                toTimestamp(task.createdAt()), toTimestamp(task.startedAt()),
                toTimestamp(task.completedAt()),
                task.boardId(), task.state(), task.assignee(), task.orderIndex(),
                task.idempotencyKey(),
                next,
                task.id(), tenantId, expected);
        if (updated == 0) {
            // Another writer raced us between the read and the update.
            return Optional.empty();
        }
        return Optional.of(task.withVersion(next));
    }

    @Override
    public Optional<TaskRecord> findById(String id) {
        return findInTenant(id, currentTenantId());
    }

    @Override
    public List<TaskRecord> findByStatus(TaskStatus status) {
        return jdbc.query("""
                SELECT * FROM jaiclaw_tasks
                 WHERE tenant_id = ? AND status = ?
                 ORDER BY created_at DESC
                """, rowMapper, currentTenantId(), status.name());
    }

    @Override
    public List<TaskRecord> findByBoardAndState(String boardId, String state) {
        if (state == null) {
            return jdbc.query("""
                    SELECT * FROM jaiclaw_tasks
                     WHERE tenant_id = ? AND board_id = ? AND state IS NULL
                     ORDER BY order_index, created_at
                    """, rowMapper, currentTenantId(), boardId);
        }
        return jdbc.query("""
                SELECT * FROM jaiclaw_tasks
                 WHERE tenant_id = ? AND board_id = ? AND state = ?
                 ORDER BY order_index, created_at
                """, rowMapper, currentTenantId(), boardId, state);
    }

    @Override
    public List<TaskRecord> findAll() {
        return jdbc.query("""
                SELECT * FROM jaiclaw_tasks
                 WHERE tenant_id = ?
                 ORDER BY created_at DESC
                """, rowMapper, currentTenantId());
    }

    @Override
    public void deleteById(String id) {
        jdbc.update("DELETE FROM jaiclaw_tasks WHERE id = ? AND tenant_id = ?",
                id, currentTenantId());
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jaiclaw_tasks WHERE tenant_id = ?",
                Long.class, currentTenantId());
        return n == null ? 0L : n;
    }

    // ── Lease API (analysis §6.5 / plan §9) ────────────────────────

    /**
     * Acquire the lease for {@code taskId} for {@code instance} until
     * {@code until}. Returns {@code true} when the claim succeeded —
     * either the row had no current claim, the previous lease expired,
     * or {@code instance} already held it.
     */
    public boolean claim(String taskId, String instance, Instant until) {
        int rows = jdbc.update("""
                UPDATE jaiclaw_tasks
                   SET claimed_by = ?, lease_until = ?
                 WHERE id = ? AND tenant_id = ?
                   AND (claimed_by IS NULL
                        OR claimed_by = ?
                        OR lease_until IS NULL
                        OR lease_until < ?)
                """,
                instance, toTimestamp(until),
                taskId, currentTenantId(),
                instance, toTimestamp(Instant.now()));
        return rows > 0;
    }

    /** Refresh the lease for {@code taskId} — caller must already hold it. */
    public boolean renewLease(String taskId, String instance, Instant until) {
        int rows = jdbc.update("""
                UPDATE jaiclaw_tasks
                   SET lease_until = ?
                 WHERE id = ? AND tenant_id = ? AND claimed_by = ?
                """,
                toTimestamp(until), taskId, currentTenantId(), instance);
        return rows > 0;
    }

    /** Release the lease — only the current holder can. */
    public boolean releaseLease(String taskId, String instance) {
        int rows = jdbc.update("""
                UPDATE jaiclaw_tasks
                   SET claimed_by = NULL, lease_until = NULL
                 WHERE id = ? AND tenant_id = ? AND claimed_by = ?
                """,
                taskId, currentTenantId(), instance);
        return rows > 0;
    }

    /** Task ids whose lease is expired — recoverable by another instance. */
    public List<String> findExpiredLeases(Instant now) {
        return jdbc.queryForList("""
                SELECT id FROM jaiclaw_tasks
                 WHERE tenant_id = ?
                   AND claimed_by IS NOT NULL
                   AND lease_until IS NOT NULL
                   AND lease_until < ?
                """, String.class, currentTenantId(), toTimestamp(now));
    }

    // ── helpers ─────────────────────────────────────────────────────

    private Optional<TaskRecord> findInTenant(String id, String tenantId) {
        List<TaskRecord> rows = jdbc.query(
                "SELECT * FROM jaiclaw_tasks WHERE id = ? AND tenant_id = ?",
                rowMapper, id, tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private void insert(TaskRecord task, String tenantId) {
        jdbc.update("""
                INSERT INTO jaiclaw_tasks
                       (id, tenant_id, name, description, status, delivery_state,
                        result, error, flow_id, metadata_json,
                        created_at, started_at, completed_at,
                        board_id, state, assignee, version, order_index,
                        idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?, ?,
                        ?, ?, ?, ?, ?,
                        ?)
                """,
                task.id(), tenantId, task.name(), task.description(),
                task.status().name(), task.deliveryState().name(),
                task.result(), task.error(), task.flowId(), writeMetadata(task.metadata()),
                toTimestamp(task.createdAt()), toTimestamp(task.startedAt()),
                toTimestamp(task.completedAt()),
                task.boardId(), task.state(), task.assignee(),
                task.version(), task.orderIndex(),
                task.idempotencyKey());
    }

    private String currentTenantId() {
        if (tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return tenantGuard.getProperties().defaultTenantId();
    }

    private String effectiveTenantId(TaskRecord task) {
        return task.tenantId() != null ? task.tenantId() : currentTenantId();
    }

    private String writeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return json.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to encode task metadata, storing null: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> readMetadata(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return json.readValue(raw, METADATA_TYPE);
        } catch (Exception e) {
            log.warn("Failed to decode task metadata, returning empty: {}", e.getMessage());
            return Map.of();
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private class TaskRowMapper implements RowMapper<TaskRecord> {
        @Override
        public TaskRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TaskRecord(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    TaskStatus.valueOf(rs.getString("status")),
                    TaskDeliveryState.valueOf(rs.getString("delivery_state")),
                    rs.getString("result"),
                    rs.getString("error"),
                    rs.getString("flow_id"),
                    readMetadata(rs.getString("metadata_json")),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("started_at")),
                    toInstant(rs.getTimestamp("completed_at")),
                    rs.getString("tenant_id"),
                    rs.getString("board_id"),
                    rs.getString("state"),
                    rs.getString("assignee"),
                    rs.getLong("version"),
                    rs.getInt("order_index"),
                    rs.getString("idempotency_key"));
        }
    }
}
