package io.jaiclaw.gateway.tenant;

import io.jaiclaw.core.tenant.AuthenticatedTenantSupplier;
import io.jaiclaw.core.tenant.TenantContext;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves the tenant from the <em>already validated</em> security principal.
 *
 * <p>Runs first ({@code order() == 5}) so a tenant established by authentication
 * always wins over one inferred from channel identifiers.
 *
 * <p>Note that {@code attributes} is ignored entirely. That is intentional and
 * is the correction this class exists to make: the {@link TenantResolver}
 * contract passes raw, pre-authentication request attributes, and the resolver
 * it replaces ({@code JwtTenantResolver}) used them to parse a tenant claim out
 * of an <em>unverified</em> JWT payload. Trust flows one way only — from the
 * authentication filter outward.
 *
 * <p>1.2.0.
 */
public class SecurityContextTenantResolver implements TenantResolver {

    private final AuthenticatedTenantSupplier supplier;

    public SecurityContextTenantResolver(AuthenticatedTenantSupplier supplier) {
        this.supplier = supplier;
    }

    @Override
    public Optional<TenantContext> resolve(Map<String, String> attributes) {
        if (supplier == null) {
            return Optional.empty();
        }
        return supplier.currentTenant();
    }

    @Override
    public int order() {
        return 5;
    }
}
