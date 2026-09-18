package io.jaiclaw.security;

import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolProfileHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jaiclaw.security.authn.JaiClawAuthentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security filter that validates JWT tokens, sets the Spring Security context,
 * propagates the tenant context to {@link TenantContextHolder}, and resolves the
 * {@link ToolProfile} via {@link RoleToolProfileResolver} into {@link ToolProfileHolder}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenValidator tokenValidator;
    private final RoleToolProfileResolver roleToolProfileResolver;

    public JwtAuthenticationFilter(JwtTokenValidator tokenValidator) {
        this(tokenValidator, null);
    }

    public JwtAuthenticationFilter(JwtTokenValidator tokenValidator,
                                   RoleToolProfileResolver roleToolProfileResolver) {
        this.tokenValidator = tokenValidator;
        this.roleToolProfileResolver = roleToolProfileResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            tokenValidator.validate(token).ifPresent(validated -> {
                var authorities = validated.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                ToolProfile profile = roleToolProfileResolver != null
                        ? roleToolProfileResolver.resolve(validated.roles())
                        : null;

                // One principal type across every mode — see JaiClawAuthentication.
                var authentication = new JaiClawAuthentication(
                        validated.subject(),
                        validated.tenantContext(),
                        profile,
                        JaiClawAuthentication.AuthSource.JWT,
                        authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Still populated here so downstream code that reads the holders
                // directly keeps working; SecurityContextTenantResolver is the
                // supported path for new code.
                TenantContextHolder.set(validated.tenantContext());
                if (profile != null) {
                    ToolProfileHolder.set(profile);
                }

                log.debug("JWT authenticated: subject={}, tenant={}, roles={}",
                        validated.subject(),
                        validated.tenantContext().getTenantId(),
                        validated.roles());
            });
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            ToolProfileHolder.clear();
        }
    }
}
