package io.jaiclaw.tools.security;

import java.security.MessageDigest;
import java.util.Map;

/**
 * Bootstrap validator for {@link BootstrapTrust#API_KEY} level.
 * Checks that the client-provided API key matches the configured key
 * using constant-time comparison to prevent timing attacks.
 */
public class ApiKeyBootstrapValidator implements BootstrapValidator {

    private static final String PARAM_API_KEY = "apiKey";

    @Override
    public boolean validate(Map<String, Object> negotiateParams, SecurityHandshakeProperties properties) {
        String configuredKey = properties.apiKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            return false;
        }

        Object provided = negotiateParams.get(PARAM_API_KEY);
        if (!(provided instanceof String providedKey) || providedKey.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                configuredKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                providedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public String failureReason() {
        return "Invalid or missing API key";
    }
}
