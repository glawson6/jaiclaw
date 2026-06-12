package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.core.Point;

/**
 * Single-line text with a maximum {@code width}. Line breaks in the
 * source string collapse to single spaces; text longer than
 * {@code width} is truncated with {@code '…'}.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.Label}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Label implements IElement {

    private static final int AUTO = Integer.MIN_VALUE;

    private final String text;
    private final int x;
    private final int y;
    private final int width;

    public Label(String text) {
        this(text, AUTO, AUTO, AUTO);
    }

    public Label(String text, int x, int y) {
        this(text, x, y, AUTO);
    }

    public Label(String text, int x, int y, int width) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public String getText() {
        return text;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        String s = text.replaceAll("[\\n\\r]+", " ");
        int drawX = x == AUTO ? 0 : x;
        int drawY = y == AUTO ? 0 : y;
        int drawW = width == AUTO ? s.length() : width;

        if (s.length() > drawW) {
            if (drawW > 1) {
                s = s.substring(0, drawW - 1) + "…";
            } else {
                s = "";
            }
        }
        canvas.draw(drawX, drawY, s);
        return new Point(drawX, drawY);
    }
}
