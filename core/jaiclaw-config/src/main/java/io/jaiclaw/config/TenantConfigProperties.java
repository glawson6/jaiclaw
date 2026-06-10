package io.jaiclaw.config;

import io.jaiclaw.core.tenant.TenantMode;

import java.util.List;

/**
 * Configuration properties for {@code jaiclaw.tenant.*}.
 *
 * @param mode                    SINGLE (default) or MULTI
 * @param defaultTenantId         default tenant ID for storage keys in SINGLE mode.
 *                                <b>Override the {@code "default"} placeholder before exposing to
 *                                untrusted callers</b> — it's used as the storage-key prefix.
 * @param strictDefaultTenantId   when true, reject low-entropy {@code defaultTenantId} values
 *                                at startup (default: false)
 * @param configLocations         directories to scan for per-tenant YAML/env files
 *                                (e.g. "classpath:config/tenants/", "file:/etc/jaiclaw/tenants/")
 * @param tenantHeader            HTTP header for tenant resolution (default: "X-Tenant-Id")
 */
public record TenantConfigProperties(
        TenantMode mode,
        String defaultTenantId,
        boolean strictDefaultTenantId,
        List<String> configLocations,
        String tenantHeader
) {
    public static final TenantConfigProperties DEFAULT = new TenantConfigProperties(null, null, false, null, null);

    public TenantConfigProperties {
        if (mode == null) mode = TenantMode.SINGLE;
        if (defaultTenantId == null || defaultTenantId.isBlank()) defaultTenantId = "default";
        if (configLocations == null) configLocations = List.of();
        if (tenantHeader == null || tenantHeader.isBlank()) tenantHeader = "X-Tenant-Id";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TenantMode mode;
        private String defaultTenantId;
        private boolean strictDefaultTenantId;
        private List<String> configLocations;
        private String tenantHeader;

        public Builder mode(TenantMode mode) { this.mode = mode; return this; }
        public Builder defaultTenantId(String defaultTenantId) { this.defaultTenantId = defaultTenantId; return this; }
        public Builder strictDefaultTenantId(boolean strict) { this.strictDefaultTenantId = strict; return this; }
        public Builder configLocations(List<String> configLocations) { this.configLocations = configLocations; return this; }
        public Builder tenantHeader(String tenantHeader) { this.tenantHeader = tenantHeader; return this; }

        public TenantConfigProperties build() {
            return new TenantConfigProperties(mode, defaultTenantId, strictDefaultTenantId, configLocations, tenantHeader);
        }
    }
}
