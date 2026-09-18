package io.jaiclaw.core.tool;

/**
 * Thread-local holder for the current {@link ToolProfile}.
 * <p>
 * Set by the security filter (when enabled) to communicate the resolved tool profile
 * to the gateway/agent layer without coupling those layers to the security module.
 * Must be cleared in a finally block after request processing.
 */
public final class ToolProfileHolder {

    private static final ThreadLocal<ToolProfile> PROFILE = new ThreadLocal<>();

    private ToolProfileHolder() {}

    public static void set(ToolProfile profile) {
        PROFILE.set(profile);
    }

    public static ToolProfile get() {
        return PROFILE.get();
    }

    /**
     * Returns the current profile, or {@link ToolProfile#FULL} if none is set.
     *
     * @deprecated This fails <strong>open</strong>: when no filter has set a
     *     profile — which is the case in {@code api-key} and {@code none}
     *     security modes, on every channel-originated message, and on the
     *     permitAll {@code /webhook/**} path — it grants unrestricted tool
     *     access. Use {@link #getOrDefault(ToolProfile)} and supply the
     *     deployment's configured default
     *     ({@code jaiclaw.security.default-tool-profile}) instead.
     *     Scheduled for removal once all callers are migrated.
     */
    @Deprecated(since = "1.2.0", forRemoval = true)
    public static ToolProfile getOrDefault() {
        return getOrDefault(ToolProfile.FULL);
    }

    /**
     * Returns the current profile, or {@code fallback} when none is set.
     *
     * <p>Keeping the fallback a parameter rather than a constant leaves
     * {@code jaiclaw-core} free of configuration concerns: the caller — which
     * does have access to {@code jaiclaw.security.default-tool-profile} —
     * decides what "no profile" means for that deployment.
     *
     * @param fallback profile to use when none is set; must not be null
     */
    public static ToolProfile getOrDefault(ToolProfile fallback) {
        java.util.Objects.requireNonNull(fallback, "fallback");
        ToolProfile p = PROFILE.get();
        return p != null ? p : fallback;
    }

    public static void clear() {
        PROFILE.remove();
    }
}
