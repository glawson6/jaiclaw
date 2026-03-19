package io.jclaw.core.tool;

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
     * This preserves backward compatibility when security is disabled.
     */
    public static ToolProfile getOrDefault() {
        ToolProfile p = PROFILE.get();
        return p != null ? p : ToolProfile.FULL;
    }

    public static void clear() {
        PROFILE.remove();
    }
}
