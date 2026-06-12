package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.core.Point;

/**
 * Box drawn with single-line Unicode box-drawing glyphs
 * ({@code ─ │ ┌ ┐ └ ┘}). The zero-arg constructor fills the canvas;
 * the four-arg constructor places the box at {@code (x, y)} with the
 * given size.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.Rectangle}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Rectangle implements IElement {

    private static final int AUTO = Integer.MIN_VALUE;

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public Rectangle() {
        this(AUTO, AUTO, AUTO, AUTO);
    }

    public Rectangle(int x, int y, int width, int height) {
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
        int drawX = x == AUTO ? 0 : x;
        int drawY = y == AUTO ? 0 : y;
        int drawW = width == AUTO ? canvas.getWidth() : width;
        int drawH = height == AUTO ? canvas.getHeight() : height;

        canvas.draw(drawX, drawY, "─", drawW);
        canvas.draw(drawX, drawY + drawH - 1, "─", drawW);
        canvas.draw(drawX, drawY, "│\n", drawH);
        canvas.draw(drawX + drawW - 1, drawY, "│\n", drawH);

        canvas.draw(drawX, drawY, "┌");
        canvas.draw(drawX + drawW - 1, drawY, "┐");
        canvas.draw(drawX, drawY + drawH - 1, "└");
        canvas.draw(drawX + drawW - 1, drawY + drawH - 1, "┘");

        return new Point(drawX, drawY);
    }
}
