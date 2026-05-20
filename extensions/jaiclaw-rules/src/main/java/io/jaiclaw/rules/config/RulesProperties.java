package io.jaiclaw.rules.config;

/**
 * Configuration properties for the rules extension.
 * Bound via {@code jaiclaw.rules.*} in auto-configuration.
 */
public record RulesProperties(
        boolean enabled
) {
    public RulesProperties() {
        this(false);
    }
}
