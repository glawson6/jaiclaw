package io.jaiclaw.asciirender.core;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Reference {@link ICanvas} implementation backed by a list of
 * {@link StringBuilder}s — one per row.
 *
 * <p>Modernisation notes vs. upstream
 * {@code com.indvd00m.ascii.render.Canvas}:
 * <ul>
 *   <li>Two near-duplicate {@code getTrimmedRegion(char)} /
 *       {@code getTrimmedRegion(char, char)} methods consolidated to
 *       one driven by an {@link IntPredicate}.</li>
 *   <li>Upstream's {@code new String(new char[n]).replace(...)}
 *       string-repeat trick replaced with {@code String.repeat(n)}
 *       (Java 11+).</li>
 *   <li>{@code String#matches} regex on every draw call replaced with
 *       a cheap {@code indexOf} pre-check.</li>
 * </ul>
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.Canvas}, original author indvd00m
 * {@code <gotoindvdum at gmail dot com>}.
 */
public class Canvas implements ICanvas {

    public static final char NULL_CHAR = '\0';

    private final int width;
    private final int height;
    private final List<StringBuilder> lines;

    private String cachedText;
    private String cachedLines;
    private boolean needUpdateCache = false;

    public Canvas(int width, int height) {
        if (width < 0) {
            throw new IllegalArgumentException("width must be >= 0, was " + width);
        }
        if (height < 0) {
            throw new IllegalArgumentException("height must be >= 0, was " + height);
        }
        this.width = width;
        this.height = height;
        this.lines = new ArrayList<>(height);
        clear();
    }

    // ── ICanvas: dimensions ──────────────────────────────────────

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    // ── ICanvas: text / cache ────────────────────────────────────

    @Override
    public String getText() {
        updateCacheIfNeeded();
        return cachedText;
    }

    @Override
    public String toString() {
        return getText();
    }

    private void updateCacheIfNeeded() {
        if (needUpdateCache) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(lines.get(i));
                if (i < lines.size() - 1) {
                    sb.append('\n');
                }
            }
            this.cachedLines = sb.toString();
            this.cachedText = cachedLines.replace(NULL_CHAR, ' ');
            this.needUpdateCache = false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Canvas other = (Canvas) o;
        updateCacheIfNeeded();
        other.updateCacheIfNeeded();
        return width == other.width
                && height == other.height
                && cachedLines.equals(other.cachedLines);
    }

    @Override
    public int hashCode() {
        updateCacheIfNeeded();
        int result = width;
        result = 31 * result + height;
        result = 31 * result + cachedLines.hashCode();
        return result;
    }

    // ── ICanvas: draw ────────────────────────────────────────────

    @Override
    public void draw(int x, int y, char c) {
        draw(x, y, String.valueOf(c));
    }

    @Override
    public void draw(int x, int y, char c, int count) {
        if (count <= 0) return;
        draw(x, y, String.valueOf(c).repeat(count));
    }

    @Override
    public void draw(int x, int y, String s) {
        if (x >= width) return;
        if (y >= height) return;

        // Multiline: split on the first \n or \r and recurse per row.
        if (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            for (String line : s.split("[\\n\\r]")) {
                draw(x, y++, line);
                if (y >= height) {
                    break;
                }
            }
            return;
        }

        // Single-line path.
        if (y < 0) return;

        if (x < 0) {
            int skip = -x;
            if (skip > s.length() - 1) {
                s = "";
            } else {
                s = s.substring(skip);
            }
        }
        if (s.length() > width - x) {
            s = s.substring(0, Math.max(0, width - x));
        }
        if (x < 0) x = 0;
        if (s.isEmpty()) return;

        StringBuilder line = lines.get(y);
        line.replace(x, x + s.length(), s);
        needUpdateCache = true;
    }

    @Override
    public void draw(int x, int y, String s, int count) {
        if (count <= 0) return;
        draw(x, y, s.repeat(count));
    }

    // ── ICanvas: per-cell access ────────────────────────────────

    @Override
    public char getChar(int x, int y) {
        if (x < 0 || x >= width) return 0;
        if (y < 0 || y >= height) return 0;
        return lines.get(y).charAt(x);
    }

    @Override
    public char setChar(int x, int y, char c) {
        if (x < 0 || x >= width) return 0;
        if (y < 0 || y >= height) return 0;
        StringBuilder line = lines.get(y);
        char previous = line.charAt(x);
        line.setCharAt(x, c);
        needUpdateCache = true;
        return previous;
    }

    @Override
    public boolean isCharDrawed(int x, int y) {
        return getChar(x, y) != NULL_CHAR;
    }

    @Override
    public void clear() {
        lines.clear();
        for (int y = 0; y < height; y++) {
            StringBuilder line = new StringBuilder(width);
            line.append(new char[width]);
            lines.add(line);
        }
        needUpdateCache = true;
    }

    // ── ICanvas: trimming ────────────────────────────────────────

    @Override
    public ICanvas trim() {
        return subCanvas(trimmedRegion(c -> c == ' ' || c == NULL_CHAR));
    }

    @Override
    public ICanvas trimSpaces() {
        return trim(' ');
    }

    @Override
    public ICanvas trimNulls() {
        return trim(NULL_CHAR);
    }

    @Override
    public ICanvas trim(char trimChar) {
        return subCanvas(trimmedRegion(c -> c == trimChar));
    }

    /**
     * Find the smallest bounding region of glyphs the predicate does
     * not match. If every glyph is trimmable, returns a 0x0 region.
     */
    private IRegion trimmedRegion(IntPredicate isTrimmable) {
        int w = getWidth();
        int h = getHeight();
        int firstX = w;
        int firstY = h;
        int lastX = 0;
        int lastY = 0;

        scanFirstX:
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!isTrimmable.test(getChar(x, y))) {
                    firstX = x;
                    break scanFirstX;
                }
            }
        }
        if (firstX != w) {
            scanFirstY:
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!isTrimmable.test(getChar(x, y))) {
                        firstY = y;
                        break scanFirstY;
                    }
                }
            }
            scanLastX:
            for (int x = w - 1; x >= 0; x--) {
                for (int y = h - 1; y >= 0; y--) {
                    if (!isTrimmable.test(getChar(x, y))) {
                        lastX = x;
                        break scanLastX;
                    }
                }
            }
            scanLastY:
            for (int y = h - 1; y >= 0; y--) {
                for (int x = w - 1; x >= 0; x--) {
                    if (!isTrimmable.test(getChar(x, y))) {
                        lastY = y;
                        break scanLastY;
                    }
                }
            }
        }
        int regionWidth = Math.max(0, lastX - firstX + 1);
        int regionHeight = Math.max(0, lastY - firstY + 1);
        return new Region(firstX, firstY, regionWidth, regionHeight);
    }

    @Override
    public ICanvas subCanvas(IRegion region) {
        int subWidth = region.getWidth();
        int subHeight = region.getHeight();
        int subX = region.getX();
        int subY = region.getY();
        ICanvas canvas = new Canvas(subWidth, subHeight);
        for (int x = 0; x < subWidth; x++) {
            for (int y = 0; y < subHeight; y++) {
                canvas.setChar(x, y, getChar(subX + x, subY + y));
            }
        }
        return canvas;
    }
}
