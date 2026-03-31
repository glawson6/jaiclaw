package io.jaiclaw.security;

import io.jaiclaw.core.tenant.DefaultTenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * API key authentication filter. Checks the {@code X-API-Key} header or
 * {@code api_key} query parameter against the resolved key from {@link ApiKeyProvider}.
 * <p>
 * Skips {@code /api/health} and {@code /webhook/**} endpoints.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_PARAM = "api_key";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private final ApiKeyProvider apiKeyProvider;
    private final TenantGuard tenantGuard;
    private final boolean timingSafe;

    public ApiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider) {
        this(apiKeyProvider, null, false);
    }

    public ApiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider, TenantGuard tenantGuard) {
        this(apiKeyProvider, tenantGuard, false);
    }

    public ApiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider, TenantGuard tenantGuard,
                                       boolean timingSafe) {
        this.apiKeyProvider = apiKeyProvider;
        this.tenantGuard = tenantGuard;
        this.timingSafe = timingSafe;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/health".equals(path) || path.startsWith("/webhook/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || providedKey.isBlank()) {
            providedKey = request.getParameter(API_KEY_PARAM);
        }

        if (providedKey == null || providedKey.isBlank()) {
            log.debug("Request to {} missing API key — set X-API-Key header or api_key query param",
                    request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        boolean keyMatch;
        if (timingSafe) {
            keyMatch = MessageDigest.isEqual(
                    providedKey.getBytes(StandardCharsets.UTF_8),
                    apiKeyProvider.getResolvedKey().getBytes(StandardCharsets.UTF_8));
        } else {
            keyMatch = providedKey.equals(apiKeyProvider.getResolvedKey());
        }

        if (!keyMatch) {
            log.debug("Invalid API key for request to {}", request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        // In MULTI mode, require X-Tenant-Id header and set TenantContext
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String tenantId = request.getHeader(TENANT_ID_HEADER);
            if (tenantId == null || tenantId.isBlank()) {
                log.debug("Multi-tenant mode: missing X-Tenant-Id header for request to {}",
                        request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"missing_tenant_id\",\"message\":\"X-Tenant-Id header is required in multi-tenant mode\"}");
                return;
            }
            TenantContextHolder.set(new DefaultTenantContext(tenantId, tenantId));
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("api-key-user", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            if (tenantGuard != null && tenantGuard.isMultiTenant()) {
                TenantContextHolder.clear();
            }
        }
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"invalid_api_key\"}");
    }
}
