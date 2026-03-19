package io.jclaw.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Logs the active security mode at startup so operators know the gateway's auth posture.
 */
public class SecurityModeLogger implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SecurityModeLogger.class);

    private final JClawSecurityProperties properties;
    private final ApiKeyProvider apiKeyProvider;

    public SecurityModeLogger(JClawSecurityProperties properties, ApiKeyProvider apiKeyProvider) {
        this.properties = properties;
        this.apiKeyProvider = apiKeyProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String mode = properties.mode();

        switch (mode) {
            case "api-key" -> logApiKeyMode();
            case "jwt" -> log.info("Security mode: jwt — JWT authentication enabled");
            case "none" -> logNoneMode();
            default -> log.warn("Unknown security mode '{}' — defaulting to permissive", mode);
        }
    }

    private void logApiKeyMode() {
        log.info("Security mode: api-key — API key: {} (source: {})",
                apiKeyProvider.getMaskedKey(), apiKeyProvider.getSource());
    }

    private void logNoneMode() {
        log.warn("");
        log.warn("╔══════════════════════════════════════════════════════════════╗");
        log.warn("║  WARNING: Security is DISABLED — all endpoints are public   ║");
        log.warn("║  Set jclaw.security.mode=api-key for API key protection     ║");
        log.warn("║  Set jclaw.security.mode=jwt for JWT authentication         ║");
        log.warn("╚══════════════════════════════════════════════════════════════╝");
        log.warn("");
    }
}
