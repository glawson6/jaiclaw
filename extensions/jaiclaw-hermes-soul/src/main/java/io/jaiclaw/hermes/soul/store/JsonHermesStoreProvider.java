package io.jaiclaw.hermes.soul.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.tenant.TenantGuard;

import java.nio.file.Path;

/**
 * Default JSON-on-disk {@link HermesStoreProvider}. Stores Souls under the
 * configured root directory; future Phase 2 + Phase 3 work will add
 * {@code MemoryStore} and {@code TendenciesStore} sub-stores here.
 *
 * <p>Plan §5 task 1.5 — default impl matching the
 * {@code JsonTaskStoreProvider} shape.
 */
public class JsonHermesStoreProvider implements HermesStoreProvider {

    public static final String TYPE = "json";

    private final FileSoulProvider soulStore;

    public JsonHermesStoreProvider(Path rootDir, TenantGuard tenantGuard) {
        this(rootDir, tenantGuard, defaultMapper());
    }

    public JsonHermesStoreProvider(Path rootDir, TenantGuard tenantGuard, ObjectMapper mapper) {
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
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return m;
    }
}
