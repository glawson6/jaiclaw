package io.jclaw.core.tenant;

/**
 * Thread-local holder for the current {@link TenantContext}.
 * <p>
 * Must be set at the gateway/channel layer before any agent execution and
 * cleared in a finally block after the request completes. Async tasks
 * (e.g., {@code @Async}) do NOT inherit the context — they must explicitly
 * receive and re-set it.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {}

    /**
     * Set the tenant context for the current thread.
     */
    public static void set(TenantContext ctx) {
        CONTEXT.set(ctx);
    }

    /**
     * Get the tenant context for the current thread, or null if none is set.
     */
    public static TenantContext get() {
        return CONTEXT.get();
    }

    /**
     * Get the tenant context for the current thread, throwing if none is set.
     *
     * @throws IllegalStateException if no tenant context is set
     */
    public static TenantContext require() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "No TenantContext set on current thread. " +
                    "Ensure tenant resolution occurs before agent execution.");
        }
        return ctx;
    }

    /**
     * Clear the tenant context for the current thread.
     * Must be called in a finally block after request processing.
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
