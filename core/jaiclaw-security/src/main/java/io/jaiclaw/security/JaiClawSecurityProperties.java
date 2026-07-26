package io.jaiclaw.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for JaiClaw security.
 * <p>
 * Security mode determines the authentication strategy:
 * <ul>
 *   <li>{@code api-key} (default) — auto-generated or explicit API key</li>
 *   <li>{@code jwt} — JWT token authentication</li>
 *   <li>{@code none} — no authentication (dev only, logs warning)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "jaiclaw.security")
public record JaiClawSecurityProperties(
        boolean enabled,
        String mode,
        String apiKey,
        String apiKeyFile,
        boolean timingSafeApiKey,
        JwtProperties jwt,
        RoleMappingProperties roleMapping,
        RateLimitProperties rateLimit,
        boolean allowNoneOnPublicBind,
        boolean requireHttps,
        ApiKeyFilterProperties apiKeyFilter
) {
    public JaiClawSecurityProperties() {
        this(false, null, null, null, true,
                new JwtProperties(), new RoleMappingProperties(), RateLimitProperties.defaults(),
                false, false, ApiKeyFilterProperties.defaults());
    }

    /**
     * Backward-compatible 9-arg constructor for pre-0.9.4 callers. Defaults
     * the new {@code requireHttps} field to {@code false} (preserves dev workflow).
     *
     * <p>Deliberately {@code private}: Spring Boot 4's record binder picks a
     * public constructor by parameter-count heuristic and can silently choose
     * a delegating overload instead of the {@code @ConstructorBinding}-annotated
     * canonical constructor — nested-record fields then receive the delegate's
     * hardcoded defaults instead of yaml values. One canonical public
     * constructor per {@code @ConfigurationProperties} record is the safe
     * shape; delegating overloads must not be visible to the binder.
     */
    @SuppressWarnings("unused")
    private JaiClawSecurityProperties(
            boolean enabled, String mode, String apiKey, String apiKeyFile,
            boolean timingSafeApiKey, JwtProperties jwt, RoleMappingProperties roleMapping,
            RateLimitProperties rateLimit, boolean allowNoneOnPublicBind) {
        this(enabled, mode, apiKey, apiKeyFile, timingSafeApiKey, jwt, roleMapping,
                rateLimit, allowNoneOnPublicBind, false, ApiKeyFilterProperties.defaults());
    }

    /**
     * Backward-compatible 10-arg constructor for callers written against the
     * {@code requireHttps} shape (post-0.9.4, pre-{@link ApiKeyFilterProperties}).
     * Defaults the new {@code apiKeyFilter} field to its skip-list defaults
     * ({@code [/api/health, /webhook/**]}).
     *
     * <p>Deliberately {@code private} — see the sibling 9-arg constructor's
     * Javadoc above for the Boot-4 record-binder rationale.
     */
    @SuppressWarnings("unused")
    private JaiClawSecurityProperties(
            boolean enabled, String mode, String apiKey, String apiKeyFile,
            boolean timingSafeApiKey, JwtProperties jwt, RoleMappingProperties roleMapping,
            RateLimitProperties rateLimit, boolean allowNoneOnPublicBind, boolean requireHttps) {
        this(enabled, mode, apiKey, apiKeyFile, timingSafeApiKey, jwt, roleMapping,
                rateLimit, allowNoneOnPublicBind, requireHttps, ApiKeyFilterProperties.defaults());
    }

    @ConstructorBinding
    public JaiClawSecurityProperties {
        // Backward compatibility: derive mode from enabled flag if mode not set explicitly.
        // Only default when mode is genuinely absent (null), not when set to an explicit value.
        if (mode == null) {
            mode = enabled ? "jwt" : "api-key";
        }
        if (apiKeyFile == null || apiKeyFile.isBlank()) {
            apiKeyFile = System.getProperty("user.home") + "/.jaiclaw/api-key";
        }
        if (jwt == null) jwt = new JwtProperties();
        if (roleMapping == null) roleMapping = new RoleMappingProperties();
        if (rateLimit == null) rateLimit = RateLimitProperties.defaults();
        if (apiKeyFilter == null) apiKeyFilter = ApiKeyFilterProperties.defaults();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean enabled;
        private String mode;
        private String apiKey;
        private String apiKeyFile;
        private boolean timingSafeApiKey;
        private JwtProperties jwt;
        private RoleMappingProperties roleMapping;
        private RateLimitProperties rateLimit;
        private boolean allowNoneOnPublicBind;
        private boolean requireHttps;
        private ApiKeyFilterProperties apiKeyFilter;

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder mode(String mode) { this.mode = mode; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder apiKeyFile(String apiKeyFile) { this.apiKeyFile = apiKeyFile; return this; }
        public Builder timingSafeApiKey(boolean timingSafeApiKey) { this.timingSafeApiKey = timingSafeApiKey; return this; }
        public Builder jwt(JwtProperties jwt) { this.jwt = jwt; return this; }
        public Builder roleMapping(RoleMappingProperties roleMapping) { this.roleMapping = roleMapping; return this; }
        public Builder rateLimit(RateLimitProperties rateLimit) { this.rateLimit = rateLimit; return this; }
        public Builder allowNoneOnPublicBind(boolean v) { this.allowNoneOnPublicBind = v; return this; }
        public Builder requireHttps(boolean v) { this.requireHttps = v; return this; }
        public Builder apiKeyFilter(ApiKeyFilterProperties apiKeyFilter) { this.apiKeyFilter = apiKeyFilter; return this; }

        public JaiClawSecurityProperties build() {
            return new JaiClawSecurityProperties(enabled, mode, apiKey, apiKeyFile, timingSafeApiKey,
                    jwt, roleMapping, rateLimit, allowNoneOnPublicBind, requireHttps, apiKeyFilter);
        }
    }

    public record JwtProperties(
            String secret,
            String issuer,
            String tenantClaim,
            String roleClaim
    ) {
        public JwtProperties() {
            this(null, null, "tenantId", "roles");
        }
    }

    public record RoleMappingProperties(
            Map<String, String> roleToProfile,
            String defaultProfile
    ) {
        public RoleMappingProperties() {
            this(Map.of(), "MINIMAL");
        }

        public RoleMappingProperties {
            if (roleToProfile == null) roleToProfile = Map.of();
            if (defaultProfile == null || defaultProfile.isBlank()) defaultProfile = "MINIMAL";
        }
    }

    public record RateLimitProperties(
            boolean enabled,
            int maxRequestsPerWindow,
            int windowSeconds,
            int cleanupIntervalSeconds,
            List<String> skipPaths
    ) {
        /**
         * No-arg default. Deliberately {@code private} — see the outer
         * {@link JaiClawSecurityProperties} 9-arg constructor's Javadoc for
         * why any additional public constructor beyond the canonical form
         * causes Spring Boot 4's record binder to silently drop yaml values
         * for this nested record. Use {@link #defaults()} for programmatic
         * defaults.
         */
        @SuppressWarnings("unused")
        private RateLimitProperties() {
            this(false, 60, 60, 300, List.of());
        }

        /** Programmatic default instance — disabled, standard limits, empty skip list. */
        public static RateLimitProperties defaults() {
            return new RateLimitProperties(false, 60, 60, 300, List.of());
        }

        /**
         * Backward-compatible 4-arg constructor for callers written before
         * {@link #skipPaths} was added. Defaults the new field to an empty
         * list (the existing {@code /api/**} whitelist gate in
         * {@link RateLimitFilter#shouldNotFilter} is the primary exclusion).
         *
         * <p>Deliberately {@code private} — see the outer
         * {@link JaiClawSecurityProperties} 9-arg constructor's Javadoc for
         * why delegating overloads must not be visible to Spring Boot 4's
         * record binder.
         */
        @SuppressWarnings("unused")
        private RateLimitProperties(boolean enabled, int maxRequestsPerWindow,
                                     int windowSeconds, int cleanupIntervalSeconds) {
            this(enabled, maxRequestsPerWindow, windowSeconds, cleanupIntervalSeconds, List.of());
        }

        public RateLimitProperties {
            if (maxRequestsPerWindow <= 0) maxRequestsPerWindow = 60;
            if (windowSeconds <= 0) windowSeconds = 60;
            if (cleanupIntervalSeconds <= 0) cleanupIntervalSeconds = 300;
            if (skipPaths == null) skipPaths = List.of();
            else skipPaths = List.copyOf(skipPaths);
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private boolean enabled;
            private int maxRequestsPerWindow;
            private int windowSeconds;
            private int cleanupIntervalSeconds;
            private List<String> skipPaths;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder maxRequestsPerWindow(int maxRequestsPerWindow) { this.maxRequestsPerWindow = maxRequestsPerWindow; return this; }
            public Builder windowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; return this; }
            public Builder cleanupIntervalSeconds(int cleanupIntervalSeconds) { this.cleanupIntervalSeconds = cleanupIntervalSeconds; return this; }
            public Builder skipPaths(List<String> skipPaths) { this.skipPaths = skipPaths; return this; }

            public RateLimitProperties build() {
                return new RateLimitProperties(enabled, maxRequestsPerWindow, windowSeconds,
                        cleanupIntervalSeconds, skipPaths);
            }
        }
    }

    /**
     * Configuration for {@link ApiKeyAuthenticationFilter}. Property path
     * {@code jaiclaw.security.api-key-filter.*}. Named to avoid a collision
     * with the existing top-level {@code apiKey} scalar
     * ({@code jaiclaw.security.api-key=<secret>}).
     *
     * @param skipPaths request paths that bypass the API-key check entirely.
     *                  Each entry is a Spring {@code PathPattern} —
     *                  {@code **} for prefix wildcards, {@code *} for a
     *                  single segment. Defaults to {@code [/api/health,
     *                  /webhook/**]} to preserve the pre-configurable
     *                  hard-coded behavior.
     */
    public record ApiKeyFilterProperties(
            List<String> skipPaths
    ) {
        /**
         * No-arg default. Deliberately {@code private} — see the outer
         * {@link JaiClawSecurityProperties} 9-arg constructor's Javadoc for
         * why any additional public constructor beyond the canonical form
         * causes Spring Boot 4's record binder to silently drop yaml values
         * for this nested record. Use {@link #defaults()} for programmatic
         * defaults.
         */
        @SuppressWarnings("unused")
        private ApiKeyFilterProperties() {
            this(null);
        }

        /** Programmatic default instance — {@code [/api/health, /webhook/**]}. */
        public static ApiKeyFilterProperties defaults() {
            return new ApiKeyFilterProperties(List.of());
        }

        public ApiKeyFilterProperties {
            if (skipPaths == null || skipPaths.isEmpty()) {
                skipPaths = List.of("/api/health", "/webhook/**");
            } else {
                skipPaths = List.copyOf(skipPaths);
            }
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private List<String> skipPaths;

            public Builder skipPaths(List<String> skipPaths) { this.skipPaths = skipPaths; return this; }

            public ApiKeyFilterProperties build() {
                return new ApiKeyFilterProperties(skipPaths);
            }
        }
    }
}
