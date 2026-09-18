package io.jaiclaw.security;

import io.jaiclaw.core.tenant.DefaultTenantContext;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.security.authn.JaiClawAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * API key authentication filter. Checks the {@code X-API-Key} header or
 * {@code api_key} query parameter against the resolved key from {@link ApiKeyProvider}.
 * <p>
 * By default skips {@code /api/health} and {@code /webhook/**} endpoints.
 * The skip list is configurable via {@code jaiclaw.security.api-key-filter.skip-paths} —
 * useful for coexisting with browser OIDC flows on the same host.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_PARAM = "api_key";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    /** Name reported for the legacy single key from {@code jaiclaw.security.api-key}. */
    static final String LEGACY_KEY_NAME = "legacy-default";
    private static final List<String> DEFAULT_SKIP_PATHS = List.of("/api/health", "/webhook/**");

    private final ApiKeyProvider apiKeyProvider;
    private final ApiKeyStore apiKeyStore;
    private final TenantGuard tenantGuard;
    private final boolean timingSafe;
    private final List<PathPattern> skipPatterns;
    private final String tenantHeaderName;

    public ApiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider) {
        this(apiKeyProvider, null, false, null);
    }

    public ApiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider, TenantGuard tenantGuard) {
        this(apiKeyProvider, tenantGuard, false, null);
    }

    public ApiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider, TenantGuard tenantGuard,
                                       boolean timingSafe) {
        this(apiKeyProvider, tenantGuard, timingSafe, null);
    }

    /**
     * @param skipPaths request paths that bypass the filter entirely. Each entry
     *                  is a Spring path pattern ({@link PathPattern} shape —
     *                  {@code **} for prefix wildcards, {@code *} for a
     *                  single segment). {@code null} or empty falls back to
     *                  the hard-coded defaults {@code [/api/health, /webhook/**]}.
     */
    public ApiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider, TenantGuard tenantGuard,
                                       boolean timingSafe, List<String> skipPaths) {
        this(apiKeyProvider, null, tenantGuard, timingSafe, skipPaths, TENANT_ID_HEADER);
    }

    /**
     * Full constructor.
     *
     * @param apiKeyStore      multi-key store consulted first; when {@code null} the
     *                         filter falls back to the legacy single key from
     *                         {@link ApiKeyProvider}
     * @param tenantHeaderName header naming the tenant the caller acts for;
     *                         {@code jaiclaw.tenant.tenant-header}, default
     *                         {@code X-Tenant-Id}
     */
    public ApiKeyAuthenticationFilter(ApiKeyProvider apiKeyProvider, ApiKeyStore apiKeyStore,
                                       TenantGuard tenantGuard, boolean timingSafe,
                                       List<String> skipPaths, String tenantHeaderName) {
        this.apiKeyProvider = apiKeyProvider;
        this.apiKeyStore = apiKeyStore;
        this.tenantGuard = tenantGuard;
        this.timingSafe = timingSafe;
        this.tenantHeaderName = (tenantHeaderName == null || tenantHeaderName.isBlank())
                ? TENANT_ID_HEADER : tenantHeaderName;
        List<String> effective = (skipPaths == null || skipPaths.isEmpty())
                ? DEFAULT_SKIP_PATHS
                : skipPaths;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.skipPatterns = effective.stream()
                .map(parser::parse)
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        for (PathPattern pattern : skipPatterns) {
            if (pattern.matches(path)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || providedKey.isBlank()) {
            providedKey = request.getParameter(API_KEY_PARAM);
            if (providedKey != null && !providedKey.isBlank()) {
                log.warn("API key provided via query parameter — this is deprecated and will be removed. "
                        + "Use the X-API-Key header instead.");
            }
        }

        if (providedKey == null || providedKey.isBlank()) {
            log.debug("Request to {} missing API key — set X-API-Key header or api_key query param",
                    request.getRequestURI());
            sendUnauthorized(response);
            return;
        }

        // ── Step 1: identify the key ────────────────────────────────────────
        // Resolving identity BEFORE any tenant check is deliberate: answering
        // 403 on a tenant mismatch before verifying the key itself would let an
        // attacker enumerate valid tenant ids using a garbage key.
        ApiKeyStore.ApiKeyIdentity identity = identify(providedKey);
        if (identity == null) {
            log.debug("Invalid API key for request to {}", request.getRequestURI());
            sendUnauthorized(response, "invalid_api_key",
                    "The supplied API key is not recognised");
            return;
        }

        // ── Step 2: bind the tenant (multi-tenant mode only) ────────────────
        TenantContext tenant = null;
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String requestedTenant = request.getHeader(tenantHeaderName);

            if (requestedTenant == null || requestedTenant.isBlank()) {
                // 401, not 400: the credential is incomplete — the caller has not
                // finished stating who they are — so the response carries a
                // WWW-Authenticate challenge.
                log.debug("Multi-tenant mode: missing {} header for request to {}",
                        tenantHeaderName, request.getRequestURI());
                sendUnauthorized(response, "missing_tenant_id",
                        tenantHeaderName + " header is required in multi-tenant mode");
                return;
            }

            if (!identity.hasTenant()) {
                // The key declares no tenant, so there is no association to check
                // the header against. Authentication cannot complete.
                log.warn("API key '{}' declares no tenant and cannot be used in multi-tenant mode "
                                + "(request to {})",
                        identity.keyName(), request.getRequestURI());
                sendUnauthorized(response, "key_has_no_tenant",
                        "This API key is not associated with any tenant");
                return;
            }

            if (!identity.tenantId().equals(requestedTenant)) {
                // Authenticated, but not authorised for the tenant named. This is a
                // genuine security signal — a credential reaching outside its
                // binding — so it is logged at WARN with the key NAME, never the key.
                log.warn("API key '{}' is bound to tenant '{}' but requested tenant '{}' "
                                + "(request to {}) — denied",
                        identity.keyName(), identity.tenantId(), requestedTenant,
                        request.getRequestURI());
                sendForbidden(response);
                return;
            }

            tenant = new DefaultTenantContext(identity.tenantId(), identity.tenantId());
        }

        // ── Step 3: emit a complete principal ───────────────────────────────
        // Roles are meaningful only in multi-tenant mode: a single-tenant
        // deployment authenticates with zero authorities, exactly as before 1.2.0.
        List<GrantedAuthority> authorities = (tenant != null && identity.role() != null)
                ? List.of(new SimpleGrantedAuthority(identity.role()))
                : List.of();

        JaiClawAuthentication authentication = new JaiClawAuthentication(
                identity.keyName(),
                tenant,
                null,   // profile falls back to jaiclaw.security.default-tool-profile
                JaiClawAuthentication.AuthSource.API_KEY,
                authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Resolve the presented key to an identity, consulting the multi-key store
     * first and falling back to the legacy single key.
     *
     * @return the bound identity, or {@code null} when the key is unknown
     */
    private ApiKeyStore.ApiKeyIdentity identify(String providedKey) {
        if (apiKeyStore != null) {
            java.util.Optional<ApiKeyStore.ApiKeyIdentity> found =
                    apiKeyStore.findByKey(providedKey);
            if (found.isPresent()) {
                return found.get();
            }
        }
        if (apiKeyProvider == null) {
            return null;
        }
        // Legacy single-key path: no tenant, no role. Works in single-tenant mode;
        // in multi-tenant mode it is rejected above for having no tenant.
        boolean keyMatch = timingSafe
                ? MessageDigest.isEqual(
                        providedKey.getBytes(StandardCharsets.UTF_8),
                        apiKeyProvider.getResolvedKey().getBytes(StandardCharsets.UTF_8))
                : providedKey.equals(apiKeyProvider.getResolvedKey());
        return keyMatch
                ? new ApiKeyStore.ApiKeyIdentity(LEGACY_KEY_NAME, null, null)
                : null;
    }

    private void sendUnauthorized(HttpServletResponse response, String error, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // A 401 advertises how to authenticate; a 403 does not.
        response.setHeader("WWW-Authenticate", "ApiKey realm=\"jaiclaw\"");
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        sendUnauthorized(response, "invalid_api_key", "The supplied API key is not recognised");
    }

    private void sendForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Deliberately does not echo the bound tenant — that would tell a caller
        // holding a valid key which other tenants exist.
        response.getWriter().write(
                "{\"error\":\"cross_tenant_denied\","
                        + "\"message\":\"This API key is not authorised for the requested tenant\"}");
    }
}
