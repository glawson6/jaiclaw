package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.core.Point;

/**
 * Single dot rendered as {@code '*'}. The zero-arg constructor defers
 * position to render time and uses the canvas centre.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.Dot}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Dot implements IElement {

    private static final int CENTERED = Integer.MIN_VALUE;

    private final int x;
    private final int y;

    public Dot() {
        this(CENTERED, CENTERED);
    }

    public Dot(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        int drawX = x == CENTERED ? canvas.getWidth() / 2 : x;
        int drawY = y == CENTERED ? canvas.getHeight() / 2 : y;
        canvas.draw(drawX, drawY, "*");
        return new Point(drawX, drawY);
    }
}
