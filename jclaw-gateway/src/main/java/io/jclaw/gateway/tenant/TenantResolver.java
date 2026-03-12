package io.jclaw.gateway.tenant;

import io.jclaw.core.tenant.TenantContext;

import java.util.Map;
import java.util.Optional;

/**
 * SPI for resolving the tenant from an inbound request.
 * Implementations are tried in order until one returns a non-empty result.
 * <p>
 * Built-in strategies:
 * <ul>
 *   <li>{@link JwtTenantResolver} — extracts tenant from JWT claims</li>
 *   <li>{@link BotTokenTenantResolver} — maps bot token to tenant</li>
 * </ul>
 */
public interface TenantResolver {

    /**
     * Attempt to resolve a tenant from the given request attributes.
     *
     * @param attributes request attributes (headers, path variables, channel metadata)
     * @return the resolved tenant, or empty if this resolver cannot handle the request
     */
    Optional<TenantContext> resolve(Map<String, String> attributes);

    /**
     * The order in which this resolver should be tried (lower = earlier).
     */
    default int order() {
        return 0;
    }
}
