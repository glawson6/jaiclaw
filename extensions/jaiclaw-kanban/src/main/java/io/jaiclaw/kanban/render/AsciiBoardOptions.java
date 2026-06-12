package io.jaiclaw.kanban.render;

/**
 * Knobs for {@link BoardAsciiRenderer}. Sensible defaults so most callers
 * can just pass {@link #DEFAULT}.
 *
 * @param width         outer canvas width in columns; clamped at runtime
 * @param style         {@link Style#FULL} (boxed columns + card boxes) or
 *                      {@link Style#COMPACT} (one-line-per-card table)
 * @param maxCardLines  maximum body lines per card box in {@code FULL}
 *                      mode (excludes header line); longer descriptions
 *                      are truncated with {@code '…'}
 * @param showHeader    include the title bar with the board name + card
 *                      count
 * @param emptyMarker   text drawn inside an empty column (e.g. "(empty)")
 */
public record AsciiBoardOptions(
        int width,
        Style style,
        int maxCardLines,
        boolean showHeader,
        String emptyMarker
) {
    public static final AsciiBoardOptions DEFAULT = new AsciiBoardOptions(
            120, Style.FULL, 2, true, "(empty)");

    public AsciiBoardOptions {
        if (width < 20) width = 20;
        if (style == null) style = Style.FULL;
        if (maxCardLines < 0) maxCardLines = 0;
        if (emptyMarker == null) emptyMarker = "(empty)";
    }

    public enum Style {
        FULL,
        COMPACT
    }
}
