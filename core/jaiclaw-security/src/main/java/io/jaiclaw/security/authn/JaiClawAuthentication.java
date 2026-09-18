package io.jaiclaw.security.authn;

import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tool.ToolProfile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The authenticated caller, together with the tenant they may act for and the
 * tool profile their credentials grant.
 *
 * <p>Every authentication filter — API key, JWT, and later OIDC — produces this
 * one type, so that tenancy and authorisation are read from a single validated
 * place rather than re-derived downstream from raw request attributes. That
 * inversion is the point: before 1.2.0 the gateway re-parsed the
 * {@code Authorization} header to find a tenant, which meant an unverified
 * token could establish tenant context.
 *
 * <p>Consumers should prefer {@link #tenantContext()} over reading headers, and
 * {@code SecurityContextTenantResolver} is the supported bridge from this
 * principal to {@code TenantContextHolder}.
 *
 * <p>1.2.0.
 */
public class JaiClawAuthentication extends AbstractAuthenticationToken {

    /** How the caller proved their identity. */
    public enum AuthSource {
        /** A configured API key, per {@code jaiclaw.security.api-keys[]}. */
        API_KEY,
        /** A JWT validated against a shared HMAC secret. */
        JWT,
        /** An OIDC access token validated against an issuer's JWKS. */
        OIDC
    }

    private final String principalName;
    private final transient TenantContext tenantContext;
    private final ToolProfile toolProfile;
    private final AuthSource source;

    /**
     * @param principalName caller identity — a key name, JWT subject, or OIDC {@code sub}
     * @param tenantContext the tenant this caller acts for; {@code null} in single-tenant mode
     * @param toolProfile   the profile granted; {@code null} means "fall back to the configured default"
     * @param source        how the caller authenticated
     * @param authorities   granted authorities; never null
     */
    public JaiClawAuthentication(String principalName,
                                 TenantContext tenantContext,
                                 ToolProfile toolProfile,
                                 AuthSource source,
                                 Collection<? extends GrantedAuthority> authorities) {
        super(authorities == null ? List.of() : authorities);
        this.principalName = Objects.requireNonNull(principalName, "principalName");
        this.tenantContext = tenantContext;
        this.toolProfile = toolProfile;
        this.source = Objects.requireNonNull(source, "source");
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return principalName;
    }

    @Override
    public Object getCredentials() {
        // Credentials are verified by the filter and never retained — holding them
        // here would put key material into every SecurityContext.
        return null;
    }

    /** The tenant this caller may act for, or empty in single-tenant mode. */
    public Optional<TenantContext> tenantContext() {
        return Optional.ofNullable(tenantContext);
    }

    /** The granted tool profile, or empty to defer to the configured default. */
    public Optional<ToolProfile> toolProfile() {
        return Optional.ofNullable(toolProfile);
    }

    /** How this caller authenticated. */
    public AuthSource source() {
        return source;
    }

    @Override
    public String toString() {
        return "JaiClawAuthentication[principal=" + principalName
                + ", source=" + source
                + ", tenant=" + (tenantContext == null ? "none" : tenantContext.getTenantId())
                + ", profile=" + (toolProfile == null ? "default" : toolProfile)
                + ", authorities=" + getAuthorities() + "]";
    }
}
