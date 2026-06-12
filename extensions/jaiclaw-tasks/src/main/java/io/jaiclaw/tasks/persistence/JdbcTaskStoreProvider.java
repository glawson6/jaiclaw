package io.jaiclaw.tasks.persistence;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.tasks.TaskStore;
import io.jaiclaw.tasks.persistence.h2.H2TaskStore;
import org.h2.Driver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.util.Map;

/**
 * {@link TaskStoreProvider} that builds an {@link H2TaskStore} from a
 * JDBC URL config. Supports types {@code "jdbc"} and {@code "h2"}
 * (synonyms — both currently delegate to the H2-flavoured row mapper).
 *
 * <p>Per-tenant configuration shape (analysis §6.7):
 * <pre>
 * jaiclaw:
 *   tasks:
 *     storage:
 *       tenants:
 *         globex:
 *           type: jdbc
 *           url:  jdbc:h2:file:~/.jaiclaw/tasks/globex
 *           user: sa
 *           password: ""
 * </pre>
 *
 * <p>Phase 4 ships H2-backed JDBC. The same provider class will be
 * adapted in Phase 4b for Postgres via a tableQuoting / dialect flag —
 * the core SQL the {@link H2TaskStore} uses is standard JDBC except for
 * {@code MERGE INTO} on the upsert path, which Postgres replaces with
 * {@code INSERT ... ON CONFLICT}. Out of scope for this commit.
 */
public class JdbcTaskStoreProvider implements TaskStoreProvider {

    private final TenantGuard tenantGuard;

    public JdbcTaskStoreProvider() {
        this(new TenantGuard(TenantProperties.DEFAULT));
    }

    public JdbcTaskStoreProvider(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard != null ? tenantGuard
                : new TenantGuard(TenantProperties.DEFAULT);
    }

    @Override
    public boolean supports(String type) {
        return "jdbc".equalsIgnoreCase(type) || "h2".equalsIgnoreCase(type);
    }

    @Override
    public TaskStore create(String tenantId, Map<String, String> config) {
        String url = config.get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "JdbcTaskStoreProvider requires 'url' in tenant config (got: " + config + ")");
        }
        String user = config.getOrDefault("user", "sa");
        String password = config.getOrDefault("password", "");
        SimpleDriverDataSource ds = new SimpleDriverDataSource(new Driver(), url, user, password);
        return new H2TaskStore(new JdbcTemplate(ds), tenantGuard);
    }
}
