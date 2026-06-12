package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.core.Point;

/**
 * Ellipse with major / minor axes equal to {@code width} / {@code height}.
 * Rendered using the locus-of-points definition (sum of distances from
 * the two foci equals {@code 2a}).
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.Ellipse}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Ellipse implements IElement {

    private static final int AUTO = Integer.MIN_VALUE;
    private static final double DISTANCE_PRECISION = 0.5d;

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public Ellipse() {
        this(AUTO, AUTO, AUTO, AUTO);
    }

    public Ellipse(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
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

    public int getHeight() {
        return height;
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        int drawX = x == AUTO ? canvas.getWidth() / 2 : x;
        int drawY = y == AUTO ? canvas.getHeight() / 2 : y;
        int drawW = width == AUTO ? canvas.getWidth() - 1 : width;
        int drawH = height == AUTO ? canvas.getHeight() - 1 : height;

        double a = drawW / 2d;
        double b = drawH / 2d;
        double c = Math.sqrt(Math.abs(a * a - b * b));
        double f1x = drawX - c;
        double f2x = drawX + c;
        double fy = drawY;

        for (int x1 = (int) (drawX - a); x1 <= drawX + a; x1++) {
            for (int y1 = (int) (drawY - b); y1 <= drawY + b; y1++) {
                double d1x = f1x - x1, d1y = fy - y1;
                double d2x = f2x - x1, d2y = fy - y1;
                double d1 = Math.sqrt(d1x * d1x + d1y * d1y);
                double d2 = Math.sqrt(d2x * d2x + d2y * d2y);
                if (Math.abs(d1 + d2 - 2 * a) < DISTANCE_PRECISION) {
                    canvas.draw(x1, y1, "*");
                }
            }
        }
        return new Point(drawX, drawY);
    }
}
