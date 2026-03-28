package io.jaiclaw.cronmanager.persistence.h2;

import io.jaiclaw.core.model.CronJob;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.cronmanager.model.CronJobDefinition;
import io.jaiclaw.cronmanager.persistence.CronJobDefinitionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * H2-backed implementation of {@link CronJobDefinitionStore}.
 */
public class H2CronJobDefinitionStore implements CronJobDefinitionStore {

    private static final Logger log = LoggerFactory.getLogger(H2CronJobDefinitionStore.class);

    private final JdbcTemplate jdbc;
    private final TenantGuard tenantGuard;
    private final RowMapper<CronJobDefinition> rowMapper = new CronJobDefinitionRowMapper();

    public H2CronJobDefinitionStore(JdbcTemplate jdbc) {
        this(jdbc, null);
    }

    public H2CronJobDefinitionStore(JdbcTemplate jdbc, TenantGuard tenantGuard) {
        this.jdbc = jdbc;
        this.tenantGuard = tenantGuard;
    }

    private String tenantParam() {
        return tenantGuard != null ? tenantGuard.requireTenantIfMulti() : null;
    }

    @Override
    public void save(CronJobDefinition def) {
        CronJob job = def.cronJob();
        String skillsCsv = String.join(",", def.skills());

        jdbc.update("""
                MERGE INTO cron_job_definitions (id, name, agent_id, schedule, timezone, prompt,
                    delivery_channel, delivery_target, enabled, last_run_at, next_run_at,
                    provider, model, system_prompt, tool_profile, skills, tenant_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                job.id(), job.name(), job.agentId(), job.schedule(), job.timezone(), job.prompt(),
                job.deliveryChannel(), job.deliveryTarget(), job.enabled(),
                toTimestamp(job.lastRunAt()), toTimestamp(job.nextRunAt()),
                def.provider(), def.model(), def.systemPrompt(),
                def.toolProfile().name(), skillsCsv, job.tenantId());
    }

    @Override
    public Optional<CronJobDefinition> findById(String id) {
        String tp = tenantParam();
        List<CronJobDefinition> results = jdbc.query(
                "SELECT * FROM cron_job_definitions WHERE id = ? AND (tenant_id = ? OR ? IS NULL)",
                rowMapper, id, tp, tp);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<CronJobDefinition> findAll() {
        String tp = tenantParam();
        return jdbc.query(
                "SELECT * FROM cron_job_definitions WHERE (tenant_id = ? OR ? IS NULL) ORDER BY name",
                rowMapper, tp, tp);
    }

    @Override
    public List<CronJobDefinition> findEnabled() {
        String tp = tenantParam();
        return jdbc.query(
                "SELECT * FROM cron_job_definitions WHERE enabled = TRUE AND (tenant_id = ? OR ? IS NULL) ORDER BY name",
                rowMapper, tp, tp);
    }

    @Override
    public boolean deleteById(String id) {
        String tp = tenantParam();
        int rows = jdbc.update(
                "DELETE FROM cron_job_definitions WHERE id = ? AND (tenant_id = ? OR ? IS NULL)",
                id, tp, tp);
        return rows > 0;
    }

    @Override
    public void updateEnabled(String id, boolean enabled) {
        String tp = tenantParam();
        jdbc.update(
                "UPDATE cron_job_definitions SET enabled = ? WHERE id = ? AND (tenant_id = ? OR ? IS NULL)",
                enabled, id, tp, tp);
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private static class CronJobDefinitionRowMapper implements RowMapper<CronJobDefinition> {
        @Override
        public CronJobDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
            CronJob cronJob = new CronJob(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("agent_id"),
                    rs.getString("schedule"),
                    rs.getString("timezone"),
                    rs.getString("prompt"),
                    rs.getString("delivery_channel"),
                    rs.getString("delivery_target"),
                    rs.getBoolean("enabled"),
                    toInstant(rs.getTimestamp("last_run_at")),
                    toInstant(rs.getTimestamp("next_run_at")),
                    rs.getString("tenant_id")
            );

            String skillsCsv = rs.getString("skills");
            List<String> skills = (skillsCsv == null || skillsCsv.isBlank())
                    ? List.of()
                    : Arrays.asList(skillsCsv.split(","));

            String profileStr = rs.getString("tool_profile");
            ToolProfile toolProfile = profileStr != null
                    ? ToolProfile.valueOf(profileStr)
                    : ToolProfile.MINIMAL;

            return new CronJobDefinition(
                    cronJob,
                    rs.getString("provider"),
                    rs.getString("model"),
                    rs.getString("system_prompt"),
                    toolProfile,
                    skills
            );
        }
    }
}
