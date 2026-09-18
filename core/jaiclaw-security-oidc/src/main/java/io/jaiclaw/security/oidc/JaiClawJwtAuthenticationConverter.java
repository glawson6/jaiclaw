package io.jaiclaw.security.oidc;

import io.jaiclaw.core.tenant.DefaultTenantContext;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.security.authn.JaiClawAuthentication;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns a validated {@link Jwt} into the {@link JaiClawAuthentication} every
 * other authentication mode produces.
 *
 * <p>This is the whole seam. Because the OIDC path emits the same principal
 * type as the API-key and JWT filters, {@code SecurityContextTenantResolver}
 * and every downstream consumer need <strong>no change</strong> to support a
 * new identity provider.
 *
 * <p>Two deliberate differences from the legacy {@code JwtAuthenticationFilter}:
 * <ul>
 *   <li>A token with <strong>no tenant claim still authenticates</strong>, with
 *       a null tenant. The legacy filter rejected such tokens outright, which
 *       meant a stock provider token failed even when correctly signed.
 *       {@code TenantGuard} decides whether a tenant is actually required.</li>
 *   <li>Authorities are prefixed {@code SCOPE_}, matching Spring Security's own
 *       convention for scope-derived authorities, so
 *       {@code @PreAuthorize("hasAuthority('SCOPE_x')")} and
 *       {@code hasAuthority} on role-style strings can coexist.</li>
 * </ul>
 *
 * <p>1.3.0.
 */
public class JaiClawJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final OidcProperties properties;
    private final ScopeToolProfileMapper profileMapper;

    public JaiClawJwtAuthenticationConverter(OidcProperties properties,
                                             ScopeToolProfileMapper profileMapper) {
        this.properties = properties;
        this.profileMapper = profileMapper;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(properties.tenantClaim());
        TenantContext tenant = (tenantId == null || tenantId.isBlank())
                ? null
                : new DefaultTenantContext(tenantId, tenantId);

        List<String> scopes = extractScopes(jwt);
        ToolProfile profile = profileMapper == null ? null : profileMapper.map(scopes);

        List<GrantedAuthority> authorities = new ArrayList<>(scopes.size());
        for (String scope : scopes) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }

        String subject = jwt.getSubject();
        return new JaiClawAuthentication(
                subject == null ? "unknown" : subject,
                tenant,
                profile,
                JaiClawAuthentication.AuthSource.OIDC,
                authorities);
    }

    /**
     * Reads the granted scopes.
     *
     * <p>Handles both shapes seen in the wild: a space-delimited {@code scope}
     * string (RFC 6749) and a {@code scp} array (Entra ID, some others).
     */
    static List<String> extractScopes(Jwt jwt) {
        Object scope = jwt.getClaim("scope");
        if (scope instanceof String s && !s.isBlank()) {
            return Arrays.stream(s.split(" ")).filter(v -> !v.isBlank()).toList();
        }
        if (scope instanceof List<?> list) {
            return list.stream().map(Object::toString).filter(v -> !v.isBlank()).toList();
        }
        Object scp = jwt.getClaim("scp");
        if (scp instanceof List<?> list) {
            return list.stream().map(Object::toString).filter(v -> !v.isBlank()).toList();
        }
        if (scp instanceof String s && !s.isBlank()) {
            return Arrays.stream(s.split(" ")).filter(v -> !v.isBlank()).toList();
        }
        return List.of();
    }
}
