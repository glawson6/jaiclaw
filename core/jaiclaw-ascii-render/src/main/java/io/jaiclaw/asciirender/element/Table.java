package io.jaiclaw.asciirender.element;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.core.Canvas;
import io.jaiclaw.asciirender.core.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid table with {@code columns × rows} cells. Each cell holds at most
 * one {@link IElement} which is rendered into its cell sub-canvas.
 * Borders are drawn with single-line Unicode glyphs.
 *
 * <p>The upstream library supports per-cell highlighting that selects
 * thicker glyphs at every neighbour-pair junction (hundreds of branch
 * combinations). For the v1 LLM tool surface we drop the highlight
 * logic and ship a single border style. If a need surfaces, port the
 * upstream's {@code CellPosition} / {@code getHighlightedNeighborCells}
 * scaffold as a follow-up.
 *
 * <p>Apache 2.0 — ported (simplified) from
 * {@code com.indvd00m.ascii.render.elements.Table}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Table implements IElement {

    private static final int AUTO = Integer.MIN_VALUE;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int columns;
    private final int rows;
    private final List<IElement> cells;

    public Table(int columns, int rows) {
        this(AUTO, AUTO, AUTO, AUTO, columns, rows);
    }

    public Table(int x, int y, int width, int height, int columns, int rows) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.columns = columns > 0 ? columns : 1;
        this.rows = rows > 0 ? rows : 1;
        int size = this.rows * this.columns;
        this.cells = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            cells.add(null);
        }
    }

    public IElement setElement(int column, int row, IElement element) {
        int index = coordToIndex(column, row);
        if (index < 0) return null;
        return cells.set(index, element);
    }

    public IElement getElement(int column, int row) {
        int index = coordToIndex(column, row);
        return index < 0 ? null : cells.get(index);
    }

    /**
     * Convert {@code (column, row)} (1-based) to a flat list index.
     * Returns -1 if out of range.
     */
    private int coordToIndex(int column, int row) {
        if (column <= 0 || row <= 0) return -1;
        if (column > columns || row > rows) return -1;
        return (row - 1) * columns + (column - 1);
    }

    private Point indexToCoord(int i) {
        return new Point(i % columns + 1, i / columns + 1);
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        int drawX = x == AUTO ? 0 : x;
        int drawY = y == AUTO ? 0 : y;
        int drawW = width == AUTO ? canvas.getWidth() : width;
        int drawH = height == AUTO ? canvas.getHeight() : height;

        drawBorders(canvas, drawX, drawY, drawW, drawH);
        drawCellContents(canvas, context, drawX, drawY, drawW, drawH);

        return new Point(drawX, drawY);
    }

    private void drawBorders(ICanvas canvas, int x0, int y0, int w, int h) {
        // Horizontal lines + corners are drawn at y = (row * h / rows) for row in [0..rows].
        // Vertical lines + corners at x = (col * w / cols) for col in [0..cols].
        // Same column-distribution math the upstream used.
        for (int r = 0; r <= rows; r++) {
            int rowY = (r == rows) ? y0 + h - 1 : y0 + r * h / rows;
            // horizontal edge
            for (int xi = x0; xi < x0 + w; xi++) {
                canvas.draw(xi, rowY, '─');
            }
            // column joints + corners on this row
            for (int c = 0; c <= columns; c++) {
                int colX = (c == columns) ? x0 + w - 1 : x0 + c * w / columns;
                char glyph;
                boolean atTop = r == 0;
                boolean atBottom = r == rows;
                boolean atLeft = c == 0;
                boolean atRight = c == columns;
                if (atTop && atLeft) glyph = '┌';
                else if (atTop && atRight) glyph = '┐';
                else if (atBottom && atLeft) glyph = '└';
                else if (atBottom && atRight) glyph = '┘';
                else if (atTop) glyph = '┬';
                else if (atBottom) glyph = '┴';
                else if (atLeft) glyph = '├';
                else if (atRight) glyph = '┤';
                else glyph = '┼';
                canvas.draw(colX, rowY, glyph);
            }
        }
        // vertical edges between horizontal rows
        for (int c = 0; c <= columns; c++) {
            int colX = (c == columns) ? x0 + w - 1 : x0 + c * w / columns;
            for (int yi = y0; yi < y0 + h; yi++) {
                if (canvas.getChar(colX, yi) == '─' || isJunction(canvas.getChar(colX, yi))) continue;
                canvas.draw(colX, yi, '│');
            }
        }
    }

    private boolean isJunction(char glyph) {
        switch (glyph) {
            case '┌': case '┐': case '└': case '┘':
            case '┬': case '┴': case '├': case '┤':
            case '┼':
                return true;
            default:
                return false;
        }
    }

    private void drawCellContents(ICanvas canvas, IContext context,
                                  int x0, int y0, int w, int h) {
        for (int i = 0; i < cells.size(); i++) {
            IElement element = cells.get(i);
            if (element == null) continue;
            Point coord = indexToCoord(i);
            int col = coord.getX();
            int row = coord.getY();
            int startX = x0 + (col - 1) * w / columns + 1;
            int startY = y0 + (row - 1) * h / rows + 1;
            int endX = x0 + col * w / columns;
            int endY = y0 + row * h / rows;
            if (endX == x0 + w) endX--;
            if (endY == y0 + h) endY--;
            int cellWidth = endX - startX;
            int cellHeight = endY - startY;
            if (cellWidth <= 0 || cellHeight <= 0) continue;
            ICanvas cellCanvas = new Canvas(cellWidth, cellHeight);
            element.draw(cellCanvas, context);
            canvas.draw(startX, startY, cellCanvas.getText());
        }
    }
}
