package io.jaiclaw.agentmind.soul.store;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.tenant.TenantGuard;

import java.nio.file.Path;

/**
 * Default JSON-on-disk {@link AgentMindStoreProvider}. Stores Souls under the
 * configured root directory; future Phase 2 + Phase 3 work will add
 * {@code MemoryStore} and {@code TendenciesStore} sub-stores here.
 *
 * <p>Plan §5 task 1.5 — default impl matching the
 * {@code JsonTaskStoreProvider} shape.
 */
public class JsonAgentMindStoreProvider implements AgentMindStoreProvider {

    public static final String TYPE = "json";

    private final FileSoulProvider soulStore;

    public JsonAgentMindStoreProvider(Path rootDir, TenantGuard tenantGuard) {
        this(rootDir, tenantGuard, defaultMapper());
    }

    public JsonAgentMindStoreProvider(Path rootDir, TenantGuard tenantGuard, ObjectMapper mapper) {
        this.soulStore = new FileSoulProvider(rootDir, tenantGuard, mapper);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public SoulProvider soulStore() {
        return soulStore;
    }

    private static ObjectMapper defaultMapper() {
        return tools.jackson.databind.json.JsonMapper.builder().build();
    }
}
