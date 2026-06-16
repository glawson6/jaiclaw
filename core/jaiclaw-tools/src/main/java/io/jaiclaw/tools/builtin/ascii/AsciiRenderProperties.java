package io.jaiclaw.tools.builtin.ascii;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * Configuration for the ascii-render profile registry. Prefix:
 * {@code jaiclaw.ascii}.
 *
 * <p>YAML shape:
 * <pre>
 * jaiclaw:
 *   ascii:
 *     default-profile: telegram_desktop   # optional; defaults to shell_80
 *     profiles:
 *       iphone_bigtext:
 *         width: 24
 *         padding: 0
 *       custom_terminal:
 *         width: 100
 *         padding: 2
 * </pre>
 *
 * <p>Operator-supplied profiles are merged into the built-in registry
 * at startup by {@code AsciiRenderProfilesInitializer}. Built-in
 * profiles (e.g. {@code telegram_mobile}, {@code slack_desktop}) are
 * present whether or not this section is configured.
 *
 * @param defaultProfile name of the registered profile used when the
 *                       LLM omits the {@code profile} parameter on the
 *                       {@code ascii_box} / {@code ascii_render} tool
 *                       call. Defaults to {@code shell_80}.
 * @param profiles       additional or overriding profile definitions,
 *                       keyed by profile name. Each entry maps to a
 *                       {@link ProfileConfig}.
 */
@ConfigurationProperties(prefix = "jaiclaw.ascii")
public record AsciiRenderProperties(
        @DefaultValue("shell_80") String defaultProfile,
        @DefaultValue Map<String, ProfileConfig> profiles
) {

    public AsciiRenderProperties {
        if (defaultProfile == null || defaultProfile.isBlank()) {
            defaultProfile = "shell_80";
        }
        if (profiles == null) {
            profiles = Map.of();
        }
    }

    /**
     * One operator-defined profile. Constraints (width range, padding
     * cap) are enforced by the {@code AsciiRenderProfile} record's
     * compact constructor when the values are registered, not at
     * binding time — so a misconfigured YAML value fails fast at
     * startup with a clear error message naming the offending profile.
     */
    public record ProfileConfig(int width, int padding) {}
}
