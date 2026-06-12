package io.jaiclaw.asciirender.api;

/**
 * Low-level character grid. After creation the canvas contains
 * {@code \0} (null) glyphs and one trailing {@code \n} per row. The
 * coordinate system has its origin at the top-left; {@code x} grows
 * rightward and {@code y} grows downward.
 *
 * <p>{@link #getText()} returns the final ASCII output with every
 * {@code \0} replaced by a space.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.api.ICanvas}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public interface ICanvas {

    /**
     * Final text with every {@code \0} replaced by {@code ' '}.
     */
    String getText();

    int getHeight();

    int getWidth();

    /**
     * Draw a single character. Coordinates outside the canvas region
     * are ignored. {@code c} may contain a line break.
     */
    void draw(int x, int y, char c);

    /**
     * Draw {@code c} {@code count} times in a horizontal run.
     */
    void draw(int x, int y, char c, int count);

    /**
     * Draw a string. {@code s} may contain line breaks.
     */
    void draw(int x, int y, String s);

    /**
     * Draw {@code s} {@code count} times in a horizontal run.
     */
    void draw(int x, int y, String s, int count);

    /**
     * Clear the canvas to {@code \0}.
     */
    void clear();

    /**
     * Read the glyph at {@code (x, y)}. Returns {@code \0} if outside
     * the canvas.
     */
    char getChar(int x, int y);

    /**
     * Set the glyph at {@code (x, y)} and return the previous value.
     */
    char setChar(int x, int y, char c);

    /**
     * True if any glyph other than {@code \0} has been drawn at
     * {@code (x, y)}.
     */
    boolean isCharDrawed(int x, int y);

    /**
     * A canvas equal to this one but with leading and trailing
     * whitespace and {@code \0} glyphs removed.
     */
    ICanvas trim();

    /**
     * A canvas equal to this one but with leading and trailing spaces
     * removed.
     */
    ICanvas trimSpaces();

    /**
     * A canvas equal to this one but with leading and trailing
     * {@code \0} glyphs removed.
     */
    ICanvas trimNulls();

    /**
     * A canvas equal to this one but with leading and trailing
     * {@code trimChar} glyphs removed.
     */
    ICanvas trim(char trimChar);

    /**
     * A subcanvas view over {@code region}.
     */
    ICanvas subCanvas(IRegion region);
}
