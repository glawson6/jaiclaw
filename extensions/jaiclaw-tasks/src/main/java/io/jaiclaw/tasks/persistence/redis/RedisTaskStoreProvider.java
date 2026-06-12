package io.jaiclaw.tasks.persistence.redis;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.tasks.TaskStore;
import io.jaiclaw.tasks.persistence.TaskStoreProvider;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

/**
 * {@link TaskStoreProvider} that builds a {@link RedisTaskStore} from
 * connection config. Plan §9 group 4b.
 *
 * <p>Config shape (per-tenant under {@code jaiclaw.tasks.storage.tenants[*]}):
 * <pre>
 * type: redis
 * host: localhost
 * port: 6379
 * key-prefix: jaiclaw:tasks    # optional
 * </pre>
 */
public class RedisTaskStoreProvider implements TaskStoreProvider {

    private final TenantGuard tenantGuard;

    public RedisTaskStoreProvider() {
        this(new TenantGuard(TenantProperties.DEFAULT));
    }

    public RedisTaskStoreProvider(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard != null ? tenantGuard
                : new TenantGuard(TenantProperties.DEFAULT);
    }

    @Override
    public boolean supports(String type) {
        return "redis".equalsIgnoreCase(type);
    }

    @Override
    public TaskStore create(String tenantId, Map<String, String> config) {
        String host = config.getOrDefault("host", "localhost");
        int port = parseInt(config.get("port"), 6379);
        String prefix = config.getOrDefault("key-prefix", "jaiclaw:tasks");

        var connFactory = new JedisConnectionFactory(
                new org.springframework.data.redis.connection.RedisStandaloneConfiguration(host, port));
        connFactory.afterPropertiesSet();
        var template = new StringRedisTemplate(connFactory);
        template.afterPropertiesSet();
        return new RedisTaskStore(template, tenantGuard, prefix);
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException e) { return fallback; }
    }
}
