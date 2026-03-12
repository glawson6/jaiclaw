package io.jclaw.gateway.tenant;

import io.jclaw.core.tenant.DefaultTenantContext;
import io.jclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the tenant from a JWT Authorization header.
 * Extracts the {@code tenantId} (or {@code programId}) claim from the JWT payload.
 * <p>
 * This resolver only parses the JWT payload to extract claims — actual signature
 * validation is handled by jclaw-security's Spring Security filter.
 */
public class JwtTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtTenantResolver.class);

    private final String tenantClaimName;

    public JwtTenantResolver() {
        this("tenantId");
    }

    public JwtTenantResolver(String tenantClaimName) {
        this.tenantClaimName = tenantClaimName;
    }

    @Override
    public Optional<TenantContext> resolve(Map<String, String> attributes) {
        String authHeader = attributes.get("authorization");
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            return Optional.empty();
        }

        String token = authHeader.substring(7).trim();
        return extractTenantFromPayload(token);
    }

    @Override
    public int order() {
        return 10;
    }

    private Optional<TenantContext> extractTenantFromPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return Optional.empty();

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            // Simple JSON extraction without a full parser (jclaw-core has no Jackson dependency)
            String tenantId = extractJsonValue(payload, tenantClaimName);
            if (tenantId == null || tenantId.isBlank()) {
                // Fall back to "programId" claim
                tenantId = extractJsonValue(payload, "programId");
            }
            if (tenantId == null || tenantId.isBlank()) return Optional.empty();

            String tenantName = extractJsonValue(payload, "tenantName");
            if (tenantName == null) tenantName = tenantId;

            log.debug("JWT tenant resolved: {} ({})", tenantId, tenantName);
            return Optional.of(new DefaultTenantContext(tenantId, tenantName));

        } catch (Exception e) {
            log.debug("Failed to extract tenant from JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Minimal JSON value extraction for a simple string field.
     * Avoids pulling in a JSON parser dependency into jclaw-gateway.
     */
    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx == -1) return null;

        // Skip whitespace
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;

        if (valueStart >= json.length()) return null;

        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd == -1) return null;
            return json.substring(valueStart + 1, valueEnd);
        }

        // Non-string value — return up to next comma or brace
        int valueEnd = valueStart;
        while (valueEnd < json.length()
                && json.charAt(valueEnd) != ','
                && json.charAt(valueEnd) != '}') {
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }
}
