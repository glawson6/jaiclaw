package io.jaiclaw.asciirender.glyph;

/**
 * Terminal detection for the ANSI colorizer. Two independent gates:
 * <ul>
 *   <li>The {@code NO_COLOR} environment variable — a widely-respected
 *       convention (see <a href="https://no-color.org">no-color.org</a>)
 *       under which any non-empty value disables ANSI output.</li>
 *   <li>{@link System#console()} — {@code null} when stdout is not a
 *       TTY (pipe, file redirect, IDE console), a real TTY otherwise.</li>
 * </ul>
 *
 * <p>Split from {@link AnsiPalette} so the palette stays pure-data and
 * every caller (tests, adopter apps) reads the same detection rules.
 */
public final class AnsiSupport {

    private AnsiSupport() {}

    /**
     * {@code true} when colour output is safe to emit — no
     * {@code NO_COLOR} override and stdout is a TTY.
     */
    public static boolean isColorEnabled() {
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null && !noColor.isEmpty()) {
            return false;
        }
        return System.console() != null;
    }
}
