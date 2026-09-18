package io.jaiclaw.gateway.tenant;

import io.jaiclaw.core.tenant.TenantContext;

import java.util.Map;
import java.util.Optional;

/**
 * SPI for resolving the tenant from an inbound request.
 * Implementations are tried in order until one returns a non-empty result.
 * <p>
 * Built-in strategies, in resolution order:
 * <ul>
 *   <li>{@link SecurityContextTenantResolver} — reads the tenant off the
 *       already-validated security principal (order 5)</li>
 *   <li>{@link BotTokenTenantResolver} — maps a bot token / workspace id to a
 *       tenant on the channel path (order 20)</li>
 * </ul>
 *
 * <p><strong>Implementor's note.</strong> {@code attributes} carries raw,
 * <em>pre-authentication</em> request data. An implementation must never treat
 * it as trusted — in particular, never parse a bearer token out of it. The
 * removed {@code JwtTenantResolver} did exactly that and allowed an unsigned
 * JWT to establish tenant context.
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
