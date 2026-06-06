package io.jaiclaw.config;

import io.jaiclaw.core.tool.CompositeToolProfile;
import io.jaiclaw.core.tool.ToolProfile;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for named {@link CompositeToolProfile} definitions.
 * Rejects names that collide with built-in {@link ToolProfile} enum values.
 *
 * <p>Plain Java class — no Spring annotations. Wired as a bean by auto-configuration.
 */
public class CompositeToolProfileRegistry {

    private final Map<String, CompositeToolProfile> profiles = new ConcurrentHashMap<>();

    /**
     * Register a composite profile. Rejects names that match a built-in {@link ToolProfile} enum value.
     *
     * @throws IllegalArgumentException if the name collides with a built-in profile
     */
    public void register(CompositeToolProfile profile) {
        if (isBuiltinProfileName(profile.name())) {
            throw new IllegalArgumentException(
                    "Composite profile name '" + profile.name() + "' collides with built-in ToolProfile enum value");
        }
        profiles.put(profile.name(), profile);
    }

    /**
     * Resolve a composite profile by name.
     */
    public Optional<CompositeToolProfile> resolve(String name) {
        return Optional.ofNullable(profiles.get(name));
    }

    /**
     * Returns an unmodifiable snapshot of all registered composite profiles.
     */
    public Map<String, CompositeToolProfile> all() {
        return Map.copyOf(profiles);
    }

    private static boolean isBuiltinProfileName(String name) {
        try {
            ToolProfile.valueOf(name.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
