package io.jclaw.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JClaw security.
 */
@ConfigurationProperties(prefix = "jclaw.security")
public record JClawSecurityProperties(
        boolean enabled,
        JwtProperties jwt
) {
    public JClawSecurityProperties() {
        this(false, new JwtProperties());
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
}
