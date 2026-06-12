package io.jaiclaw.tasks.persistence;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.tasks.JsonFileTaskStore;
import io.jaiclaw.tasks.TaskStore;

import java.nio.file.Path;
import java.util.Map;

/**
 * Default {@link TaskStoreProvider} backed by {@link JsonFileTaskStore}.
 *
 * <p>Phase 1 ships this as the only provider — single-provider for now;
 * Phase 4 adds Redis/JDBC alongside without breaking changes to the SPI.
 */
public class JsonTaskStoreProvider implements TaskStoreProvider {

    private static final String TYPE = "json";

    private final TenantGuard tenantGuard;

    public JsonTaskStoreProvider() {
        this(new TenantGuard(TenantProperties.DEFAULT));
    }

    public JsonTaskStoreProvider(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
    }

    @Override
    public boolean supports(String type) {
        return TYPE.equalsIgnoreCase(type);
    }

    @Override
    public TaskStore create(String tenantId, Map<String, String> config) {
        String dir = config.getOrDefault("dir",
                System.getProperty("user.home") + "/.jaiclaw/tasks");
        boolean ignoreCorrupt = Boolean.parseBoolean(config.getOrDefault("ignore-corrupt", "false"));
        Path path = Path.of(expandHome(dir));
        return new JsonFileTaskStore(path, tenantGuard, ignoreCorrupt);
    }

    private static String expandHome(String path) {
        if (path.startsWith("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
