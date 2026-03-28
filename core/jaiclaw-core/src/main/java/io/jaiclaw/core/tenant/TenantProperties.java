package io.jaiclaw.core.tenant;

/**
 * Configuration properties for JaiClaw tenant mode.
 * <p>
 * Bind to {@code jaiclaw.tenant.*} via Spring Boot's {@code @ConfigurationProperties}
 * or construct manually for non-Spring usage.
 *
 * <pre>
 * # Single-tenant (default — no config needed)
 * jaiclaw.tenant.mode: single
 *
 * # Multi-tenant (explicit opt-in)
 * jaiclaw.tenant.mode: multi
 * </pre>
 *
 * @param mode            SINGLE (default) or MULTI
 * @param defaultTenantId optional default tenant ID for storage keys in SINGLE mode (default: "default")
 */
public record TenantProperties(
        TenantMode mode,
        String defaultTenantId
) {
    /** Default: single-tenant, defaultTenantId = "default". */
    public static final TenantProperties DEFAULT = new TenantProperties(null, null);

    public TenantProperties {
        if (mode == null) mode = TenantMode.SINGLE;
        if (defaultTenantId == null || defaultTenantId.isBlank()) defaultTenantId = "default";
    }

    public boolean isMultiTenant() {
        return mode == TenantMode.MULTI;
    }
}
