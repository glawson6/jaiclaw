package io.jaiclaw.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        RateLimitProperties rateLimit
) {
    public JaiClawSecurityProperties() {
        this(false, null, null, null, false,
                new JwtProperties(), new RoleMappingProperties(), new RateLimitProperties());
    }

    public JaiClawSecurityProperties {
        // Backward compatibility: derive mode from enabled flag if mode not set explicitly
        if (mode == null || mode.isBlank()) {
            if (enabled) {
                mode = "jwt";
            } else {
                mode = "api-key";
            }
        }
        if (apiKeyFile == null || apiKeyFile.isBlank()) {
            apiKeyFile = System.getProperty("user.home") + "/.jaiclaw/api-key";
        }
        if (jwt == null) jwt = new JwtProperties();
        if (roleMapping == null) roleMapping = new RoleMappingProperties();
        if (rateLimit == null) rateLimit = new RateLimitProperties();
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

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder mode(String mode) { this.mode = mode; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder apiKeyFile(String apiKeyFile) { this.apiKeyFile = apiKeyFile; return this; }
        public Builder timingSafeApiKey(boolean timingSafeApiKey) { this.timingSafeApiKey = timingSafeApiKey; return this; }
        public Builder jwt(JwtProperties jwt) { this.jwt = jwt; return this; }
        public Builder roleMapping(RoleMappingProperties roleMapping) { this.roleMapping = roleMapping; return this; }
        public Builder rateLimit(RateLimitProperties rateLimit) { this.rateLimit = rateLimit; return this; }

        public JaiClawSecurityProperties build() {
            return new JaiClawSecurityProperties(enabled, mode, apiKey, apiKeyFile, timingSafeApiKey,
                    jwt, roleMapping, rateLimit);
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
            int cleanupIntervalSeconds
    ) {
        public RateLimitProperties() {
            this(false, 60, 60, 300);
        }

        public RateLimitProperties {
            if (maxRequestsPerWindow <= 0) maxRequestsPerWindow = 60;
            if (windowSeconds <= 0) windowSeconds = 60;
            if (cleanupIntervalSeconds <= 0) cleanupIntervalSeconds = 300;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private boolean enabled;
            private int maxRequestsPerWindow;
            private int windowSeconds;
            private int cleanupIntervalSeconds;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder maxRequestsPerWindow(int maxRequestsPerWindow) { this.maxRequestsPerWindow = maxRequestsPerWindow; return this; }
            public Builder windowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; return this; }
            public Builder cleanupIntervalSeconds(int cleanupIntervalSeconds) { this.cleanupIntervalSeconds = cleanupIntervalSeconds; return this; }

            public RateLimitProperties build() {
                return new RateLimitProperties(enabled, maxRequestsPerWindow, windowSeconds, cleanupIntervalSeconds);
            }
        }
    }
}
