package io.jaiclaw.security.authn;

import io.jaiclaw.core.tenant.AuthenticatedTenantSupplier;
import io.jaiclaw.core.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Reads the tenant off the validated {@link JaiClawAuthentication} in the
 * current {@link SecurityContextHolder}.
 *
 * <p>Consults only the security context — never request headers — so it is
 * structurally incapable of establishing a tenant from unverified input. That
 * is the whole point: it replaces {@code JwtTenantResolver}, which scraped a
 * tenant claim out of an unsigned JWT payload.
 *
 * <p>An {@link Authentication} of some other type yields empty rather than a
 * guess: only a principal JaiClaw minted carries a tenant it can vouch for.
 *
 * <p>1.2.0.
 */
public class SecurityContextAuthenticatedTenantSupplier implements AuthenticatedTenantSupplier {

    @Override
    public Optional<TenantContext> currentTenant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        if (auth instanceof JaiClawAuthentication jaiclaw) {
            return jaiclaw.tenantContext();
        }
        return Optional.empty();
    }
}
