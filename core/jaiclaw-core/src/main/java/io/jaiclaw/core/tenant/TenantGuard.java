package io.jaiclaw.core.tenant;

/**
 * Central tenant resolution utility. All components that need tenant-awareness
 * should inject {@code TenantGuard} instead of directly calling {@link TenantContextHolder}.
 *
 * <p>Provides three resolution strategies:
 * <ul>
 *   <li>{@link #requireTenantIfMulti()} — Returns tenantId or null. In MULTI mode, throws if no context.</li>
 *   <li>{@link #resolveTenantIdForStorage()} — Returns tenantId for storage key construction.
 *       In SINGLE mode returns "default". In MULTI mode throws if no context.</li>
 *   <li>{@link #resolveTenantPrefix()} — Returns tenantId for prefixing keys/paths.
 *       In SINGLE mode returns empty string. In MULTI mode returns tenantId.</li>
 * </ul>
 *
 * <h3>Key resolution examples:</h3>
 * <table>
 *   <tr><th>Layer</th><th>SINGLE</th><th>MULTI</th></tr>
 *   <tr><td>File path</td><td>{baseDir}/</td><td>{baseDir}/{tenantId}/</td></tr>
 *   <tr><td>SQL WHERE</td><td>No filter</td><td>AND tenant_id = ?</td></tr>
 *   <tr><td>Map key</td><td>No prefix</td><td>{tenantId}:</td></tr>
 *   <tr><td>JSON filter</td><td>No filter</td><td>Filter by tenantId</td></tr>
 * </table>
 */
public class TenantGuard {

    private final TenantProperties props;

    public TenantGuard(TenantProperties props) {
        this.props = props != null ? props : TenantProperties.DEFAULT;
    }

    /**
     * Returns tenantId or null. In MULTI mode, throws if no tenant context is set.
     */
    public String requireTenantIfMulti() {
        TenantContext ctx = TenantContextHolder.get();
        if (props.isMultiTenant()) {
            if (ctx == null) {
                throw new IllegalStateException(
                        "Multi-tenant mode is active but no TenantContext is set on the current thread. " +
                        "Ensure tenant resolution occurs before this operation.");
            }
            return ctx.getTenantId();
        }
        return ctx != null ? ctx.getTenantId() : null;
    }

    /**
     * Returns tenantId for storage key construction.
     * In SINGLE mode, returns the configured defaultTenantId ("default").
     * In MULTI mode, returns the current tenant's ID (throws if not set).
     */
    public String resolveTenantIdForStorage() {
        TenantContext ctx = TenantContextHolder.get();
        if (props.isMultiTenant()) {
            if (ctx == null) {
                throw new IllegalStateException(
                        "Multi-tenant mode is active but no TenantContext is set. " +
                        "Cannot resolve tenant ID for storage.");
            }
            return ctx.getTenantId();
        }
        return props.defaultTenantId();
    }

    /**
     * Returns tenantId for prefixing keys/paths.
     * In SINGLE mode, returns empty string (no prefix needed).
     * In MULTI mode, returns the current tenant's ID (throws if not set).
     *
     * <p><b>Legacy semantics.</b> This method is retained for backwards
     * compatibility with on-disk file layouts: SINGLE-mode callers that
     * conditionally prefix paths only when {@link #isMultiTenant()} is true
     * rely on the empty-string return. New code that wants the
     * "always prefix" invariant should use {@link #resolveStorageKey(String)}
     * instead.
     */
    public String resolveTenantPrefix() {
        if (!props.isMultiTenant()) {
            return "";
        }
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "Multi-tenant mode is active but no TenantContext is set. " +
                    "Cannot resolve tenant prefix.");
        }
        return ctx.getTenantId();
    }

    /**
     * Build a tenant-prefixed storage key in the form
     * {@code "<tenantId>:<businessId>"}, uniformly in SINGLE and MULTI mode.
     *
     * <p>In SINGLE mode the tenantId is sourced from
     * {@link TenantProperties#defaultTenantId()} (default {@code "default"};
     * <b>operators must override this in production</b> — see the WARN
     * logged at startup by {@code TenantGuard.afterPropertiesSet}). In MULTI
     * mode it is the current thread's {@link TenantContext#getTenantId()};
     * throws {@link IllegalStateException} if no context is set.
     *
     * <p>This is the preferred key-construction primitive for any in-memory
     * store that holds business data. It guarantees the prefix-always
     * invariant the {@code TenantIsolationGuardSpec} regression guard
     * enforces.
     *
     * @param businessId the business-domain id to be prefixed (must not be null)
     * @return the prefixed key, never null and never empty
     */
    public String resolveStorageKey(String businessId) {
        if (businessId == null) {
            throw new IllegalArgumentException("businessId must not be null");
        }
        TenantContext ctx = TenantContextHolder.get();
        if (props.isMultiTenant()) {
            if (ctx == null) {
                throw new IllegalStateException(
                        "Multi-tenant mode is active but no TenantContext is set. " +
                        "Cannot resolve storage key.");
            }
            return ctx.getTenantId() + ":" + businessId;
        }
        return props.defaultTenantId() + ":" + businessId;
    }

    /**
     * Strict variant of {@link #resolveStorageKey(String)} that only accepts a
     * single argument. Returns the current effective tenant prefix
     * ({@code defaultTenantId} in SINGLE, current tenant's id in MULTI) with
     * no trailing colon. Use when the caller wants to build a tenant-scoped
     * directory path or a {@code startsWith} filter.
     */
    public String resolveStoragePrefix() {
        TenantContext ctx = TenantContextHolder.get();
        if (props.isMultiTenant()) {
            if (ctx == null) {
                throw new IllegalStateException(
                        "Multi-tenant mode is active but no TenantContext is set. " +
                        "Cannot resolve storage prefix.");
            }
            return ctx.getTenantId();
        }
        return props.defaultTenantId();
    }

    /**
     * Returns true if multi-tenant mode is active.
     */
    public boolean isMultiTenant() {
        return props.isMultiTenant();
    }

    /**
     * Returns the underlying tenant properties.
     */
    public TenantProperties getProperties() {
        return props;
    }
}
