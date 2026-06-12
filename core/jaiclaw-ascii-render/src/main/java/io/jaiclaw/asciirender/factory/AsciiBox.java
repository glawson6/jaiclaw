package io.jaiclaw.asciirender.factory;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.core.Point;
import io.jaiclaw.asciirender.core.Region;
import io.jaiclaw.asciirender.core.Render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wrap a block of text in a Unicode-bordered ASCII box. Handles word
 * wrapping, four border styles, and an optional title rendered on the
 * top edge.
 *
 * <p>This is the library facade behind the {@code ascii_box} built-in
 * tool and the matching Anthropic Claude Skill JBang script. Pure
 * static API; no Spring, no SLF4J coupling — callers handle their own
 * logging when they encounter an unknown style via {@link Style#resolve}.
 *
 * <pre>{@code
 * String boxed = AsciiBox.render(
 *     "Build green — all tests passing.",
 *     60,
 *     AsciiBox.Style.DOUBLE,
 *     "STATUS");
 * }</pre>
 *
 * <p>Output is trimmed of trailing whitespace and sized just large
 * enough to contain the wrapped content.
 */
public final class AsciiBox {

    /** Default inner content width when the caller does not specify one. */
    public static final int DEFAULT_WIDTH = 60;
    public static final int MIN_WIDTH = 4;
    public static final int MAX_WIDTH = 500;

    private AsciiBox() {}

    /**
     * Render {@code content} inside a box.
     *
     * @param content non-null text; embedded newlines honoured, long
     *                lines wrap at word boundaries
     * @param width   maximum inner content width; clamped to
     *                {@code [MIN_WIDTH, MAX_WIDTH]}; values {@code <= 0}
     *                fall back to {@link #DEFAULT_WIDTH}
     * @param style   border style; {@code null} treated as
     *                {@link Style#SINGLE}
     * @param title   optional title; rendered on the top edge as
     *                {@code "[ title ]"}; ignored if {@code null} or blank
     * @return trimmed ASCII output
     */
    public static String render(String content, int width, Style style, String title) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        int clampedWidth = clampWidth(width);
        Style effectiveStyle = style == null ? Style.SINGLE : style;

        List<String> lines = wordWrap(content, clampedWidth);
        int innerWidth = computeInnerWidth(lines, clampedWidth, title);
        int totalWidth = innerWidth + 2;
        int totalHeight = lines.size() + 2;

        ICanvas canvas = new Render().render(
                Render.builder()
                        .width(totalWidth)
                        .height(totalHeight)
                        .layer(new Region(0, 0, totalWidth, totalHeight))
                        .element(new BorderedBox(effectiveStyle, lines, innerWidth, title))
                        .build());
        return canvas.trim().getText();
    }

    /** Convenience: single-border style, no title. */
    public static String render(String content, int width) {
        return render(content, width, Style.SINGLE, null);
    }

    /** Convenience: default width, single-border style, no title. */
    public static String render(String content) {
        return render(content, DEFAULT_WIDTH, Style.SINGLE, null);
    }

    // ── word wrap + sizing ───────────────────────────────────────────

    static List<String> wordWrap(String content, int width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : content.split("\\r?\\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" +")) {
                if (word.length() > width) {
                    if (current.length() > 0) {
                        out.add(current.toString());
                        current.setLength(0);
                    }
                    int idx = 0;
                    while (idx < word.length()) {
                        int end = Math.min(idx + width, word.length());
                        out.add(word.substring(idx, end));
                        idx = end;
                    }
                    continue;
                }
                if (current.length() == 0) {
                    current.append(word);
                } else if (current.length() + 1 + word.length() <= width) {
                    current.append(' ').append(word);
                } else {
                    out.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (current.length() > 0) {
                out.add(current.toString());
            }
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    private static int clampWidth(int width) {
        if (width <= 0) {
            return DEFAULT_WIDTH;
        }
        if (width < MIN_WIDTH) {
            return MIN_WIDTH;
        }
        if (width > MAX_WIDTH) {
            return MAX_WIDTH;
        }
        return width;
    }

    private static int computeInnerWidth(List<String> lines, int defaultWidth, String title) {
        int max = 0;
        for (String line : lines) {
            if (line.length() > max) {
                max = line.length();
            }
        }
        if (title != null && !title.isBlank()) {
            int titleSpan = title.length() + 4; // "[ title ]"
            if (titleSpan > max) {
                max = titleSpan;
            }
        }
        if (max < defaultWidth) {
            max = defaultWidth;
        }
        return max;
    }

    // ── border styles ────────────────────────────────────────────────

    /** Glyph set for the four supported border styles. */
    public enum Style {
        SINGLE('─', '│', '┌', '┐', '└', '┘'),
        DOUBLE('═', '║', '╔', '╗', '╚', '╝'),
        BOLD('━', '┃', '┏', '┓', '┗', '┛'),
        ROUNDED('─', '│', '╭', '╮', '╰', '╯');

        private final char horizontal;
        private final char vertical;
        private final char topLeft;
        private final char topRight;
        private final char bottomLeft;
        private final char bottomRight;

        Style(char horizontal, char vertical,
              char topLeft, char topRight,
              char bottomLeft, char bottomRight) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomLeft = bottomLeft;
            this.bottomRight = bottomRight;
        }

        public char horizontal()  { return horizontal; }
        public char vertical()    { return vertical; }
        public char topLeft()     { return topLeft; }
        public char topRight()    { return topRight; }
        public char bottomLeft()  { return bottomLeft; }
        public char bottomRight() { return bottomRight; }

        /**
         * Map a string identifier to a {@link Style}, returning
         * {@code null} for unknown identifiers so callers can decide
         * whether to log + fall back or surface an error.
         *
         * <p>Recognised values (case-insensitive, whitespace-trimmed):
         * {@code single}, {@code double}, {@code bold}, {@code heavy}
         * (alias for {@code bold}), {@code rounded}, {@code round}
         * (alias for {@code rounded}).
         */
        public static Style resolve(String key) {
            if (key == null) {
                return null;
            }
            String normalised = key.toLowerCase(Locale.ROOT).trim();
            return switch (normalised) {
                case "single" -> SINGLE;
                case "double" -> DOUBLE;
                case "bold", "heavy" -> BOLD;
                case "rounded", "round" -> ROUNDED;
                default -> null;
            };
        }
    }

    // ── internal element ────────────────────────────────────────────

    /** Element that paints a bordered box with embedded title + wrapped lines. */
    private static final class BorderedBox implements IElement {
        private final Style style;
        private final List<String> lines;
        private final int innerWidth;
        private final String title;

        BorderedBox(Style style, List<String> lines, int innerWidth, String title) {
            this.style = style;
            this.lines = lines;
            this.innerWidth = innerWidth;
            this.title = title;
        }

        @Override
        public IPoint draw(ICanvas canvas, IContext context) {
            int width = innerWidth + 2;
            int height = lines.size() + 2;

            canvas.draw(0, 0, String.valueOf(style.horizontal()), width);
            canvas.draw(0, height - 1, String.valueOf(style.horizontal()), width);
            canvas.draw(0, 0, style.vertical() + "\n", height);
            canvas.draw(width - 1, 0, style.vertical() + "\n", height);
            canvas.draw(0, 0, String.valueOf(style.topLeft()));
            canvas.draw(width - 1, 0, String.valueOf(style.topRight()));
            canvas.draw(0, height - 1, String.valueOf(style.bottomLeft()));
            canvas.draw(width - 1, height - 1, String.valueOf(style.bottomRight()));

            if (title != null && !title.isBlank()) {
                String banner = "[ " + title + " ]";
                int x = 2;
                if (x + banner.length() < width - 1) {
                    canvas.draw(x, 0, banner);
                }
            }
            for (int i = 0; i < lines.size(); i++) {
                canvas.draw(1, i + 1, lines.get(i));
            }
            return new Point(0, 0);
        }
    }
}
