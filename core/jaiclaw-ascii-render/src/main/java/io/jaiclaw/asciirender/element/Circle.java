package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.core.Point;

/**
 * Circle of a particular radius rendered with {@code '*'} glyphs.
 * Defaults to centred on the canvas with radius half the smaller
 * canvas dimension.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.Circle}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Circle implements IElement {

    private static final int AUTO = Integer.MIN_VALUE;
    private static final double DISTANCE_PRECISION = 0.5d;

    private final int x;
    private final int y;
    private final int radius;

    public Circle() {
        this(AUTO, AUTO, AUTO);
    }

    public Circle(int x, int y, int radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getRadius() {
        return radius;
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        int drawX = x == AUTO ? canvas.getWidth() / 2 : x;
        int drawY = y == AUTO ? canvas.getHeight() / 2 : y;
        int drawR = radius == AUTO
                ? Math.min(canvas.getWidth(), canvas.getHeight()) / 2
                : radius;

        for (int x1 = drawX - drawR; x1 <= drawX + drawR; x1++) {
            for (int y1 = drawY - drawR; y1 <= drawY + drawR; y1++) {
                double dx = drawX - x1;
                double dy = drawY - y1;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (Math.abs(drawR - distance) < DISTANCE_PRECISION) {
                    canvas.draw(x1, y1, "*");
                }
            }
        }
        return new Point(drawX, drawY);
    }
}
