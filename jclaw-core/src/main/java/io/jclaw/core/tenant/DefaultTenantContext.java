package io.jclaw.core.tenant;

import java.util.Map;

/**
 * Immutable record implementation of {@link TenantContext}.
 */
public record DefaultTenantContext(
        String getTenantId,
        String getTenantName,
        Map<String, Object> getMetadata
) implements TenantContext {

    public DefaultTenantContext {
        if (getTenantId == null || getTenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        getMetadata = getMetadata != null ? Map.copyOf(getMetadata) : Map.of();
    }

    public DefaultTenantContext(String tenantId, String tenantName) {
        this(tenantId, tenantName, Map.of());
    }
}
