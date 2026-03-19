package io.jclaw.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration properties for JClaw security.
 * <p>
 * Security mode determines the authentication strategy:
 * <ul>
 *   <li>{@code api-key} (default) — auto-generated or explicit API key</li>
 *   <li>{@code jwt} — JWT token authentication</li>
 *   <li>{@code none} — no authentication (dev only, logs warning)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "jclaw.security")
public record JClawSecurityProperties(
        boolean enabled,
        String mode,
        String apiKey,
        String apiKeyFile,
        JwtProperties jwt,
        RoleMappingProperties roleMapping,
        RateLimitProperties rateLimit
) {
    public JClawSecurityProperties() {
        this(false, null, null, null,
                new JwtProperties(), new RoleMappingProperties(), new RateLimitProperties());
    }

    public JClawSecurityProperties {
        // Backward compatibility: derive mode from enabled flag if mode not set explicitly
        if (mode == null || mode.isBlank()) {
            if (enabled) {
                mode = "jwt";
            } else {
                mode = "api-key";
            }
        }
        if (apiKeyFile == null || apiKeyFile.isBlank()) {
            apiKeyFile = System.getProperty("user.home") + "/.jclaw/api-key";
        }
        if (jwt == null) jwt = new JwtProperties();
        if (roleMapping == null) roleMapping = new RoleMappingProperties();
        if (rateLimit == null) rateLimit = new RateLimitProperties();
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
    }
}
