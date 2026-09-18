package io.jaiclaw.security.oidc;

import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tool.ToolProfileHolder;
import io.jaiclaw.security.authn.JaiClawAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Copies tenant and tool profile from the validated principal into the
 * thread-local holders, and clears them afterwards.
 *
 * <p>Spring Security populates the {@code SecurityContext}; a good deal of
 * JaiClaw reads {@code TenantContextHolder} and {@code ToolProfileHolder}
 * directly. This filter is the bridge, mirroring what
 * {@code JwtAuthenticationFilter} does in the legacy mode.
 *
 * <p>Note the direction of travel: the holders are populated <em>from</em> the
 * already-validated principal, never from the request. New code should prefer
 * reading the principal, via {@code SecurityContextTenantResolver}.
 *
 * <p>1.3.0.
 */
public class OidcPrincipalContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OidcPrincipalContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean populated = false;

        if (auth instanceof JaiClawAuthentication jaiclaw) {
            jaiclaw.tenantContext().ifPresent(TenantContextHolder::set);
            jaiclaw.toolProfile().ifPresent(ToolProfileHolder::set);
            populated = true;
            log.debug("OIDC authenticated: subject={}, tenant={}, profile={}",
                    jaiclaw.getPrincipal(),
                    jaiclaw.tenantContext().map(t -> t.getTenantId()).orElse("none"),
                    jaiclaw.toolProfile().map(Enum::name).orElse("default"));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (populated) {
                TenantContextHolder.clear();
                ToolProfileHolder.clear();
            }
        }
    }
}
