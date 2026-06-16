package io.jaiclaw.asciirender.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe registry of {@link AsciiRenderProfile} values. Backs the
 * {@code profile} parameter on the {@code ascii_box} and
 * {@code ascii_render} tools.
 *
 * <p>The registry ships with a curated set of built-in profiles for
 * common channels (Telegram desktop / mobile, Slack desktop / mobile,
 * Discord desktop / mobile, plain shell at 80 / 120 columns, and a
 * plain-text email default). Operators add or override profiles via
 * the {@code jaiclaw.ascii.profiles.*} configuration namespace; the
 * {@code AsciiRenderProfilesInitializer} bean applies those values to
 * this registry at startup.
 *
 * <p>The default fallback profile — used when an LLM omits the
 * {@code profile} parameter — is {@code shell_80}. Operators can
 * change it via {@code jaiclaw.ascii.default-profile} or via
 * {@link #setDefault(String)} at runtime.
 */
public final class AsciiRenderProfiles {

    private static final Logger log = LoggerFactory.getLogger(AsciiRenderProfiles.class);

    /** Fallback profile name when none is configured. */
    public static final String FALLBACK_DEFAULT = "shell_80";

    private static final Map<String, AsciiRenderProfile> REGISTRY = new ConcurrentHashMap<>();
    private static final AtomicReference<String> DEFAULT_NAME = new AtomicReference<>(FALLBACK_DEFAULT);

    static {
        registerBuiltIns();
    }

    private AsciiRenderProfiles() {}

    /**
     * Repopulate the built-in profile set. Mostly useful for tests that
     * need a clean slate after registering test-only profiles.
     */
    public static void registerBuiltIns() {
        Map<String, AsciiRenderProfile> seed = new LinkedHashMap<>();
        seed.put("shell_80",         new AsciiRenderProfile("shell_80",         78, 1));
        seed.put("shell_120",        new AsciiRenderProfile("shell_120",        118, 1));
        seed.put("telegram_desktop", new AsciiRenderProfile("telegram_desktop", 78, 1));
        seed.put("telegram_mobile",  new AsciiRenderProfile("telegram_mobile",  30, 0));
        seed.put("slack_desktop",    new AsciiRenderProfile("slack_desktop",    100, 1));
        seed.put("slack_mobile",     new AsciiRenderProfile("slack_mobile",     36, 0));
        seed.put("discord_desktop",  new AsciiRenderProfile("discord_desktop",  100, 1));
        seed.put("discord_mobile",   new AsciiRenderProfile("discord_mobile",   36, 0));
        seed.put("email",            new AsciiRenderProfile("email",            78, 1));
        REGISTRY.clear();
        REGISTRY.putAll(seed);
        DEFAULT_NAME.set(FALLBACK_DEFAULT);
    }

    /** Look up a profile by name. Returns {@code null} if absent. */
    public static AsciiRenderProfile get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return REGISTRY.get(name);
    }

    /**
     * Resolve a profile, falling back to {@link #defaultProfile()} when
     * the named profile is absent or {@code null}/blank.
     *
     * <p>Logs a {@code WARN} when a non-blank name fails to resolve, so
     * agents that typo a profile name leave a breadcrumb in the log
     * instead of silently getting default behaviour.
     */
    public static AsciiRenderProfile getOrDefault(String name) {
        if (name == null || name.isBlank()) {
            return defaultProfile();
        }
        AsciiRenderProfile p = REGISTRY.get(name);
        if (p == null) {
            log.warn("Unknown ASCII render profile '{}' — falling back to default '{}'. "
                    + "Known profiles: {}", name, DEFAULT_NAME.get(), names());
            return defaultProfile();
        }
        return p;
    }

    /**
     * Add or replace a profile by name. Used by the configuration
     * initializer to merge operator-supplied profiles into the registry.
     */
    public static void register(AsciiRenderProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        REGISTRY.put(profile.name(), profile);
    }

    /**
     * Change the default-fallback profile. Validates that the named
     * profile exists; throws otherwise so a typo in
     * {@code jaiclaw.ascii.default-profile} is caught at startup
     * rather than at the first render call.
     */
    public static void setDefault(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("default profile name must not be null or blank");
        }
        if (!REGISTRY.containsKey(name)) {
            throw new IllegalArgumentException(
                    "Cannot set default profile to '" + name + "' — not registered. "
                            + "Known profiles: " + names());
        }
        DEFAULT_NAME.set(name);
    }

    /** Name of the active default profile. */
    public static String defaultName() {
        return DEFAULT_NAME.get();
    }

    /**
     * The active default profile. Always returns a non-null value — if
     * the configured default name has gone missing, falls back to the
     * built-in {@code shell_80} guarantee.
     */
    public static AsciiRenderProfile defaultProfile() {
        AsciiRenderProfile p = REGISTRY.get(DEFAULT_NAME.get());
        if (p != null) {
            return p;
        }
        AsciiRenderProfile fallback = REGISTRY.get(FALLBACK_DEFAULT);
        if (fallback != null) {
            return fallback;
        }
        // Final safety net — should never trigger because registerBuiltIns()
        // is called from the static initializer above.
        return new AsciiRenderProfile(FALLBACK_DEFAULT, 78, 1);
    }

    /** Read-only snapshot of registered profile names. */
    public static Set<String> names() {
        return Set.copyOf(REGISTRY.keySet());
    }

    /** Read-only snapshot of the registry. Useful for diagnostics. */
    public static Map<String, AsciiRenderProfile> snapshot() {
        return Map.copyOf(REGISTRY);
    }
}
