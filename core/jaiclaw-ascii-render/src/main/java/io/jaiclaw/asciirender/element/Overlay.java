package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.api.IRegion;
import io.jaiclaw.asciirender.core.Point;
import io.jaiclaw.asciirender.core.Region;

/**
 * Compose a pre-rendered {@link ICanvas} onto the current canvas. Lets
 * a caller use one render's output as an element of another — useful
 * for caching, tiling, or stitching independently-rendered scenes.
 *
 * <p>If {@code opacity} is true, every glyph in the overlay
 * (including {@code \0}) overwrites the underlying canvas. Otherwise
 * only "drawn" glyphs pass through.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.Overlay}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Overlay implements IElement {

    private static final int AUTO = Integer.MIN_VALUE;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final ICanvas overlay;
    private final boolean opacity;

    public Overlay(ICanvas overlay) {
        this(AUTO, AUTO, AUTO, AUTO, overlay, false);
    }

    public Overlay(ICanvas overlay, boolean opacity) {
        this(AUTO, AUTO, AUTO, AUTO, overlay, opacity);
    }

    public Overlay(int x, int y, ICanvas overlay) {
        this(x, y, AUTO, AUTO, overlay, false);
    }

    public Overlay(int x, int y, ICanvas overlay, boolean opacity) {
        this(x, y, AUTO, AUTO, overlay, opacity);
    }

    public Overlay(int x, int y, int width, int height, ICanvas overlay) {
        this(x, y, width, height, overlay, false);
    }

    public Overlay(int x, int y, int width, int height, ICanvas overlay, boolean opacity) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.overlay = overlay;
        this.opacity = opacity;
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        int drawX = x == AUTO ? 0 : x;
        int drawY = y == AUTO ? 0 : y;
        int drawW = width == AUTO ? overlay.getWidth() : width;
        int drawH = height == AUTO ? overlay.getHeight() : height;
        IRegion region = new Region(drawX, drawY, drawW, drawH);

        for (int cx = region.getX(); cx < region.getX() + region.getWidth(); cx++) {
            for (int cy = region.getY(); cy < region.getY() + region.getHeight(); cy++) {
                int ox = cx - region.getX();
                int oy = cy - region.getY();
                if (opacity || overlay.isCharDrawed(ox, oy)) {
                    canvas.draw(cx, cy, overlay.getChar(ox, oy));
                }
            }
        }
        return new Point(drawX, drawY);
    }
}
