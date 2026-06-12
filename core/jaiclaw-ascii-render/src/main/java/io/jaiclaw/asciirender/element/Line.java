package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;

/**
 * Straight line from {@code start} to {@code end} drawn one glyph per
 * step using Bresenham's algorithm. Default pen is {@code '●'}.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.Line}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}. The bookkeeping
 * pass that built a List&lt;IPoint&gt; before drawing has been folded
 * into the loop — same Bresenham step, fewer allocations.
 */
public class Line implements IElement {

    public static final char DEFAULT_PEN = '●';

    private final IPoint start;
    private final IPoint end;
    private final char pen;

    public Line(IPoint start, IPoint end) {
        this(start, end, DEFAULT_PEN);
    }

    public Line(IPoint start, IPoint end, char pen) {
        this.start = start;
        this.end = end;
        this.pen = pen;
    }

    public IPoint getStart() {
        return start;
    }

    public IPoint getEnd() {
        return end;
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        int x1 = start.getX();
        int x2 = end.getX();
        int y1 = start.getY();
        int y2 = end.getY();

        int w = x2 - x1;
        int h = y2 - y1;

        int dx1 = Integer.signum(w);
        int dy1 = Integer.signum(h);
        int dx2 = Integer.signum(w);
        int dy2 = 0;

        int longest = Math.abs(w);
        int shortest = Math.abs(h);
        if (longest <= shortest) {
            longest = Math.abs(h);
            shortest = Math.abs(w);
            dy2 = Integer.signum(h);
            dx2 = 0;
        }

        int numerator = longest >> 1;
        int x = x1;
        int y = y1;
        for (int i = 0; i <= longest; i++) {
            canvas.draw(x, y, pen);
            numerator += shortest;
            if (numerator >= longest) {
                numerator -= longest;
                x += dx1;
                y += dy1;
            } else {
                x += dx2;
                y += dy2;
            }
        }
        return start;
    }
}
