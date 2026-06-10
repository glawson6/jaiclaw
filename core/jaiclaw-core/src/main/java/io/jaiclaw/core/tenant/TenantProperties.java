package io.jaiclaw.core.tenant;

import java.util.regex.Pattern;

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
 *
 * # Hardened SINGLE-mode deploy (production)
 * jaiclaw.tenant.default-tenant-id: 7f4e1f3a-9b2c-4a8e-bf76-a31c4d7e8f01
 * jaiclaw.tenant.strict-default-tenant-id: true
 * </pre>
 *
 * <h3>Security note on {@code defaultTenantId}</h3>
 *
 * <p>{@link TenantGuard#resolveStorageKey(String)} uses {@code defaultTenantId}
 * as the storage-key prefix in SINGLE mode (it falls back to the literal
 * string {@code "default"} when the property is not set). Production
 * deployments <b>must</b> override this to a high-entropy value, otherwise
 * an attacker who can influence tenant-id headers could probe the predictable
 * {@code "default:"} namespace. Set {@code strictDefaultTenantId=true} to
 * make weak values an outright startup failure.
 *
 * @param mode                    SINGLE (default) or MULTI
 * @param defaultTenantId         default tenant ID used as the storage-key
 *                                prefix in SINGLE mode (default: {@code "default"};
 *                                <b>override this in production</b>)
 * @param strictDefaultTenantId   when true, reject low-entropy {@code defaultTenantId}
 *                                values (length &lt; 16 or matches {@code [a-z]+})
 *                                at construction time (default: false)
 */
public record TenantProperties(
        TenantMode mode,
        String defaultTenantId,
        boolean strictDefaultTenantId
) {
    /** Literal value of the placeholder defaultTenantId. Operators must override this in production. */
    public static final String PLACEHOLDER_DEFAULT_TENANT_ID = "default";

    /** Default: single-tenant, defaultTenantId = "default", strict mode off. */
    public static final TenantProperties DEFAULT = new TenantProperties(null, null, false);

    /** Pattern that recognises obviously low-entropy defaultTenantId values. */
    private static final Pattern WEAK_DEFAULT_TENANT_ID_PATTERN = Pattern.compile("^[a-z]+$");

    public TenantProperties {
        if (mode == null) mode = TenantMode.SINGLE;
        if (defaultTenantId == null || defaultTenantId.isBlank()) defaultTenantId = PLACEHOLDER_DEFAULT_TENANT_ID;
        if (strictDefaultTenantId && isWeak(defaultTenantId)) {
            throw new IllegalArgumentException(
                    "jaiclaw.tenant.strict-default-tenant-id=true rejects the configured "
                            + "defaultTenantId='" + defaultTenantId + "'. Use a high-entropy value "
                            + "(e.g., a UUID; length >= 16 chars; not all lowercase letters).");
        }
    }

    /** Legacy 2-arg constructor for callers that don't supply the strict flag. */
    public TenantProperties(TenantMode mode, String defaultTenantId) {
        this(mode, defaultTenantId, false);
    }

    public boolean isMultiTenant() {
        return mode == TenantMode.MULTI;
    }

    /**
     * Returns true when {@code defaultTenantId} is still the literal
     * {@link #PLACEHOLDER_DEFAULT_TENANT_ID}. Used by {@code TenantGuard} to
     * decide whether to emit the startup security warning.
     */
    public boolean isUsingPlaceholderDefaultTenantId() {
        return PLACEHOLDER_DEFAULT_TENANT_ID.equals(defaultTenantId);
    }

    /** Visible for testing. */
    static boolean isWeak(String value) {
        if (value == null || value.length() < 16) return true;
        return WEAK_DEFAULT_TENANT_ID_PATTERN.matcher(value).matches();
    }
}
