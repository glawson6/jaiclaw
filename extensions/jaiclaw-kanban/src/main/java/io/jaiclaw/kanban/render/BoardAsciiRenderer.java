package io.jaiclaw.kanban.render;

import io.jaiclaw.asciirender.core.Canvas;
import io.jaiclaw.kanban.model.BoardSnapshot;
import io.jaiclaw.kanban.model.CardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link BoardSnapshot} into an ASCII string using
 * {@code jaiclaw-ascii-render}'s {@link Canvas}.
 *
 * <p>Two styles (see {@link AsciiBoardOptions.Style}):
 * <ul>
 *   <li>{@code FULL} — outer box, header bar, one boxed column per
 *       {@link BoardSnapshot.ColumnSnapshot} with stacked card boxes
 *       inside. Matches the layout sketched in the analysis §3.7
 *       sample.</li>
 *   <li>{@code COMPACT} — one line per card: state, id, name. Useful when
 *       width is tight or the renderer is feeding a structured log.</li>
 * </ul>
 *
 * <p>Deterministic output: identical snapshots produce identical text, so
 * the golden-snapshot Spock specs in {@code BoardAsciiRendererSpec} stay
 * stable. Card timestamps and ages are deliberately omitted because they
 * change between runs.
 */
public class BoardAsciiRenderer {

    /** Convenience overload using {@link AsciiBoardOptions#DEFAULT}. */
    public String render(BoardSnapshot snapshot) {
        return render(snapshot, AsciiBoardOptions.DEFAULT);
    }

    public String render(BoardSnapshot snapshot, AsciiBoardOptions options) {
        if (snapshot == null) return "";
        return switch (options.style()) {
            case FULL -> renderFull(snapshot, options);
            case COMPACT -> renderCompact(snapshot, options);
        };
    }

    // ── FULL style ──────────────────────────────────────────────────

    private String renderFull(BoardSnapshot snapshot, AsciiBoardOptions options) {
        int outerWidth = options.width();
        int colCount = Math.max(1, snapshot.columns().size());
        int innerWidth = outerWidth - 2;                          // outer side borders
        int gutter = colCount > 1 ? 1 : 0;
        int totalGutter = gutter * (colCount - 1);
        int columnWidth = Math.max(8, (innerWidth - totalGutter) / colCount);

        int headerRows = options.showHeader() ? 2 : 0;            // title + blank
        int columnHeaderRows = 2;                                 // "NAME (n/wip)" + blank
        int cardBoxRows = 2 + options.maxCardLines();             // top border + body + bottom border
        int cardGap = 1;
        int maxCards = snapshot.columns().stream()
                .mapToInt(c -> c.cards().size()).max().orElse(0);
        int columnsContentRows = columnHeaderRows
                + (cardBoxRows * maxCards)
                + (cardGap * Math.max(0, maxCards - 1));
        if (maxCards == 0) {
            // single empty marker line under the column header
            columnsContentRows = columnHeaderRows + 1;
        }
        int totalHeight = 2                                       // outer top/bottom borders
                + headerRows
                + columnsContentRows
                + 1;                                              // inner padding below

        Canvas canvas = new Canvas(outerWidth, Math.max(6, totalHeight));
        drawOuterFrame(canvas, options.showHeader()
                ? titleBar(snapshot, outerWidth)
                : null);

        int contentTop = 1 + headerRows;                          // skip outer top border + header
        int x = 1;                                                // skip outer left border
        for (int i = 0; i < snapshot.columns().size(); i++) {
            BoardSnapshot.ColumnSnapshot col = snapshot.columns().get(i);
            drawColumn(canvas, x, contentTop, columnWidth, col, options, cardBoxRows, cardGap);
            x += columnWidth + gutter;
        }
        return canvas.getText();
    }

    private void drawOuterFrame(Canvas canvas, String titleBar) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        canvas.draw(0, 0, "─", w);
        canvas.draw(0, h - 1, "─", w);
        canvas.draw(0, 0, "│\n", h);
        canvas.draw(w - 1, 0, "│\n", h);
        canvas.draw(0, 0, "┌");
        canvas.draw(w - 1, 0, "┐");
        canvas.draw(0, h - 1, "└");
        canvas.draw(w - 1, h - 1, "┘");
        if (titleBar != null) {
            canvas.draw(2, 0, titleBar);
        }
    }

    private String titleBar(BoardSnapshot snapshot, int outerWidth) {
        // "─ Board Name ─ N cards ─" — clamped so it never crashes through the corner.
        int available = outerWidth - 4;
        String label = " " + safeName(snapshot.boardName()) + " — " + snapshot.totalCards() + " cards ";
        if (label.length() > available) {
            label = label.substring(0, Math.max(0, available - 1)) + "…";
        }
        return label;
    }

    private void drawColumn(
            Canvas canvas, int x, int top, int width,
            BoardSnapshot.ColumnSnapshot col,
            AsciiBoardOptions options,
            int cardBoxRows,
            int cardGap) {

        String header = formatColumnHeader(col);
        if (header.length() > width) {
            header = header.substring(0, Math.max(0, width - 1)) + "…";
        }
        canvas.draw(x, top, header);

        int cardsTop = top + 2;
        if (col.cards().isEmpty()) {
            canvas.draw(x, cardsTop, truncate(options.emptyMarker(), width));
            return;
        }
        int cy = cardsTop;
        for (CardView card : col.cards()) {
            drawCardBox(canvas, x, cy, width, cardBoxRows, card, options);
            cy += cardBoxRows + cardGap;
        }
    }

    private String formatColumnHeader(BoardSnapshot.ColumnSnapshot col) {
        String label = col.name() != null ? col.name() : col.state();
        if (col.wipLimit() != null) {
            return label.toUpperCase() + " (" + col.cardCount() + "/" + col.wipLimit() + ")";
        }
        return label.toUpperCase() + " (" + col.cardCount() + ")";
    }

    private void drawCardBox(Canvas canvas, int x, int y, int width, int boxRows,
                             CardView card, AsciiBoardOptions options) {
        // Top + bottom borders.
        canvas.draw(x, y, "┌");
        canvas.draw(x + 1, y, "─", width - 2);
        canvas.draw(x + width - 1, y, "┐");
        canvas.draw(x, y + boxRows - 1, "└");
        canvas.draw(x + 1, y + boxRows - 1, "─", width - 2);
        canvas.draw(x + width - 1, y + boxRows - 1, "┘");
        // Side borders.
        for (int row = 1; row < boxRows - 1; row++) {
            canvas.draw(x, y + row, "│");
            canvas.draw(x + width - 1, y + row, "│");
        }
        // Body.
        int innerWidth = width - 4;                               // borders + 1 padding each side
        if (innerWidth <= 0) return;
        List<String> lines = buildCardBody(card, innerWidth, options.maxCardLines() + 1);
        for (int i = 0; i < lines.size() && i < boxRows - 2; i++) {
            canvas.draw(x + 2, y + 1 + i, lines.get(i));
        }
    }

    private List<String> buildCardBody(CardView card, int innerWidth, int maxLines) {
        List<String> out = new ArrayList<>();
        String idTag = "#" + shortId(card.id());
        String headerLine = truncate(idTag + " " + safeName(card.name()), innerWidth);
        out.add(headerLine);
        if (card.description() != null && !card.description().isBlank() && maxLines > 1) {
            out.addAll(wrap(card.description(), innerWidth, maxLines - 1));
        }
        return out;
    }

    // ── COMPACT style ───────────────────────────────────────────────

    private String renderCompact(BoardSnapshot snapshot, AsciiBoardOptions options) {
        StringBuilder sb = new StringBuilder();
        if (options.showHeader()) {
            sb.append(safeName(snapshot.boardName()))
                    .append(" — ")
                    .append(snapshot.totalCards())
                    .append(" cards\n");
        }
        // Width budget per column: state (12), id (8), name (rest), each padded to one space.
        int stateW = 12;
        int idW = 8;
        int nameW = Math.max(8, options.width() - stateW - idW - 2);
        sb.append(pad("STATE", stateW)).append(' ')
                .append(pad("ID", idW)).append(' ')
                .append(pad("NAME", nameW)).append('\n');
        for (BoardSnapshot.ColumnSnapshot col : snapshot.columns()) {
            for (CardView card : col.cards()) {
                sb.append(pad(col.state(), stateW)).append(' ')
                        .append(pad(shortId(card.id()), idW)).append(' ')
                        .append(pad(truncate(safeName(card.name()), nameW), nameW)).append('\n');
            }
        }
        return sb.toString();
    }

    // ── helpers ─────────────────────────────────────────────────────

    private static String shortId(String id) {
        if (id == null) return "????";
        String clean = id.replace("-", "");
        return clean.length() <= 4 ? clean : clean.substring(0, 4);
    }

    private static String safeName(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int width) {
        if (s == null) return "";
        if (s.length() <= width) return s;
        if (width <= 1) return "…";
        return s.substring(0, width - 1) + "…";
    }

    private static String pad(String s, int width) {
        String t = s == null ? "" : s;
        if (t.length() >= width) return t.substring(0, width);
        StringBuilder sb = new StringBuilder(width);
        sb.append(t);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static List<String> wrap(String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || maxLines <= 0) return lines;
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word.length() > width ? word.substring(0, width) : word);
            } else if (current.length() + 1 + word.length() <= width) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                if (lines.size() == maxLines - 1) {
                    // Last visible line: pack what's left and truncate.
                    current.setLength(0);
                    current.append(truncate(word, width));
                } else {
                    current.setLength(0);
                    current.append(word.length() > width ? word.substring(0, width) : word);
                }
            }
            if (lines.size() >= maxLines) break;
        }
        if (lines.size() < maxLines && current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
