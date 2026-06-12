package io.jaiclaw.tasks.persistence;

import io.jaiclaw.tasks.TaskStore;

import java.util.Map;

/**
 * SPI for contributing alternative {@link TaskStore} backends.
 *
 * <p>Phase 1 ships a single provider ({@code JsonTaskStoreProvider}); Phase 4
 * adds Redis and JDBC providers behind the same shape, plus a tenant-routing
 * store that resolves the per-tenant backend through this SPI. Each provider
 * owns its own isolation mechanics — key prefixing, tenant subdirectories,
 * {@code WHERE tenant_id = ?}, etc. The router only routes.
 *
 * <p>Implementations must honour the {@link TaskStore#compareAndSave} contract
 * — JSON: in-map CAS on {@code version}; Redis: {@code WATCH}/{@code MULTI}
 * or a Lua script; JDBC: {@code UPDATE … WHERE id=? AND version=?} row count
 * — so semantics cannot drift across backends.
 */
public interface TaskStoreProvider {

    /** Whether this provider can build a store of the given config {@code type}. */
    boolean supports(String type);

    /**
     * Create a tenant-scoped store. {@code tenantId} is {@code null} for the
     * default backend in {@code SINGLE} mode; non-null when constructed for a
     * specific tenant under the routing layer.
     */
    TaskStore create(String tenantId, Map<String, String> config);
}
