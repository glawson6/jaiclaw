package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.core.Point;

/**
 * Multiline word-wrapped text block with a width and height. Source
 * text is wrapped at {@code width} columns; if the resulting block
 * exceeds {@code height} rows the last visible character is replaced
 * with {@code '…'}.
 *
 * <p>The wrapping algorithm is the upstream column-counter ported
 * verbatim — it inserts {@code '\n'} every {@code width} characters
 * unless the source already has a break.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.Text}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Text implements IElement {

    private static final int AUTO = Integer.MIN_VALUE;

    private final String text;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public Text(String text) {
        this(text, AUTO, AUTO, AUTO, AUTO);
    }

    public Text(String text, int x, int y, int width, int height) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
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

    public int getHeight() {
        return height;
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        int drawX = x == AUTO ? 0 : x;
        int drawY = y == AUTO ? 0 : y;
        int drawW = width == AUTO ? canvas.getWidth() : width;
        int drawH = height == AUTO ? canvas.getHeight() : height;

        if (drawH <= 0 || drawW <= 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder(text);
        int breaksCount = 0;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (i - drawW - breaksCount == breaksCount * drawW) {
                if (c != '\n' && c != '\r') {
                    c = '\n';
                    sb.insert(i, c);
                }
            }
            if (c == '\n' || c == '\r') {
                breaksCount++;
            }
            if (breaksCount > drawH - 1) {
                for (int j = i; j >= 0; j--) {
                    char prevC = sb.charAt(j);
                    if (prevC != '\n' && prevC != '\r') {
                        sb.setCharAt(j, '…');
                        break;
                    }
                }
                sb.setLength(i + 1);
                break;
            }
        }
        canvas.draw(drawX, drawY, sb.toString());
        return new Point(drawX, drawY);
    }
}
