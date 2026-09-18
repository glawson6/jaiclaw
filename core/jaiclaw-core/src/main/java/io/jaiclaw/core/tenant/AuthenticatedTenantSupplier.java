package io.jaiclaw.core.tenant;

import io.jaiclaw.core.api.Stable;

import java.util.Optional;

/**
 * Supplies the tenant established by <em>authentication</em>, as opposed to one
 * derived from raw request attributes.
 *
 * <p>This exists to keep the trust boundary one-directional. Before 1.2.0 the
 * gateway resolved tenancy by re-parsing the {@code Authorization} header, which
 * meant an unverified token could establish tenant context. The fix is that an
 * authentication filter produces a principal carrying the tenant, and the
 * gateway only ever <em>reads</em> it.
 *
 * <p>The indirection through this interface is deliberate: {@code jaiclaw-gateway}
 * treats Spring Security as an optional dependency, so it cannot reference
 * {@code JaiClawAuthentication} directly. The implementation lives in
 * {@code jaiclaw-security}, which owns that type.
 *
 * <p>1.2.0.
 */
@Stable
@FunctionalInterface
public interface AuthenticatedTenantSupplier {

    /**
     * The tenant carried by the current validated principal, or empty when no
     * caller is authenticated or the principal carries no tenant.
     *
     * <p>Implementations must never consult request headers, only the
     * already-validated security context.
     */
    Optional<TenantContext> currentTenant();
}
