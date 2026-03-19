package io.jclaw.security;

import io.jclaw.core.tenant.TenantContextHolder;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolProfileHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
                // Set Spring Security context
                var authorities = validated.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                var authentication = new UsernamePasswordAuthenticationToken(
                        validated.subject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Set tenant context
                TenantContextHolder.set(validated.tenantContext());

                // Set tool profile based on roles
                if (roleToolProfileResolver != null) {
                    ToolProfile profile = roleToolProfileResolver.resolve(validated.roles());
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
