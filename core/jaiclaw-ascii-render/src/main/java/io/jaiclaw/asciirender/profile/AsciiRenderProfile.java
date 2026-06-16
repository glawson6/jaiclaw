package io.jaiclaw.asciirender.profile;

import io.jaiclaw.asciirender.factory.AsciiBox;

/**
 * Named bundle of width + padding constraints used by the
 * {@code ascii_box} and {@code ascii_render} tools to size their
 * output for a specific channel or environment.
 *
 * <p>Profiles let an LLM say "render this for Telegram mobile" instead
 * of guessing a hard-coded width. The framework ships a curated set of
 * profiles (see {@link AsciiRenderProfiles#registerBuiltIns}); operators
 * can add or override profiles via the {@code jaiclaw.ascii.profiles.*}
 * configuration namespace.
 *
 * <p>Width is clamped against
 * {@link AsciiBox#MIN_WIDTH}/{@link AsciiBox#MAX_WIDTH} to keep
 * profile-resolved values inside the renderer's safe range. Padding
 * is capped at {@code 16} columns/rows — enough for any reasonable
 * margin, low enough to prevent runaway canvas sizing from a typo'd
 * config value.
 *
 * @param name    profile identifier — must be non-blank; case-sensitive
 * @param width   inner content width in characters
 * @param padding uniform inner margin (columns and rows of blank space
 *                inside the canvas / box before content is drawn)
 */
public record AsciiRenderProfile(String name, int width, int padding) {

    public static final int MAX_PADDING = 16;

    public AsciiRenderProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("AsciiRenderProfile name must not be null or blank");
        }
        if (width < AsciiBox.MIN_WIDTH || width > AsciiBox.MAX_WIDTH) {
            throw new IllegalArgumentException(
                    "AsciiRenderProfile width " + width + " out of range ["
                            + AsciiBox.MIN_WIDTH + ", " + AsciiBox.MAX_WIDTH + "]");
        }
        if (padding < 0 || padding > MAX_PADDING) {
            throw new IllegalArgumentException(
                    "AsciiRenderProfile padding " + padding + " out of range [0, " + MAX_PADDING + "]");
        }
    }
}
