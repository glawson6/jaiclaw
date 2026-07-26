package io.jaiclaw.pipeline.render;

import java.util.Locale;

/**
 * Named render target. Each value carries a character width used by the
 * ASCII renderers to size tables and boxes so the output fits cleanly in
 * the target chat/terminal. The set mirrors what the
 * {@code jaiclaw-ascii-render} module exposes.
 *
 * <p>The HTML renderer ignores this profile — browsers lay out responsively.
 */
public enum RenderProfile {

    SHELL_80(80),
    SHELL_120(120),
    SLACK_DESKTOP(90),
    SLACK_MOBILE(45),
    TELEGRAM_DESKTOP(80),
    TELEGRAM_MOBILE(45),
    DISCORD_DESKTOP(100),
    DISCORD_MOBILE(45),
    EMAIL(78);

    private final int width;

    RenderProfile(int width) {
        this.width = width;
    }

    /** Character width the ASCII renderers should fit their output to. */
    public int width() {
        return width;
    }

    /**
     * Case-insensitive parse. Accepts both {@code SHELL_80} and
     * {@code shell_80} / {@code shell-80}. Returns {@link #SHELL_80} on
     * null / blank / unknown so REST endpoints and CLI flags degrade
     * gracefully.
     */
    public static RenderProfile fromString(String value) {
        if (value == null || value.isBlank()) {
            return SHELL_80;
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
        try {
            return RenderProfile.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return SHELL_80;
        }
    }
}
