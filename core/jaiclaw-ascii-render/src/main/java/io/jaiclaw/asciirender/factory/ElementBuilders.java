package io.jaiclaw.asciirender.factory;

import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.core.Point;
import io.jaiclaw.asciirender.core.Region;
import io.jaiclaw.asciirender.element.Circle;
import io.jaiclaw.asciirender.element.Dot;
import io.jaiclaw.asciirender.element.Ellipse;
import io.jaiclaw.asciirender.element.Label;
import io.jaiclaw.asciirender.element.Line;
import io.jaiclaw.asciirender.element.Rectangle;
import io.jaiclaw.asciirender.element.Table;
import io.jaiclaw.asciirender.element.Text;
import io.jaiclaw.asciirender.element.plot.IPlotPoint;
import io.jaiclaw.asciirender.element.plot.Plot;
import io.jaiclaw.asciirender.element.plot.PlotPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.jaiclaw.asciirender.factory.ParamCoercions.intArg;
import static io.jaiclaw.asciirender.factory.ParamCoercions.requireString;
import static io.jaiclaw.asciirender.factory.ParamCoercions.stringArg;

/**
 * One static factory per supported element type. Each method owns the
 * sentinel-aware "if any positional key is present, use the full
 * constructor; otherwise use the no-arg constructor" pattern locally so
 * adding or removing a kind doesn't churn the rest of the dispatch.
 *
 * <p>Throws {@link IllegalArgumentException} on malformed params.
 * {@link AsciiSceneFactory} wraps those into {@link SceneSpecException}
 * with element index + type.
 */
final class ElementBuilders {

    private static final int AUTO = Integer.MIN_VALUE;

    private ElementBuilders() {}

    static Rectangle buildRectangle(Map<String, Object> p) {
        if (p.isEmpty()) {
            return new Rectangle();
        }
        return new Rectangle(
                intArg(p, "x", AUTO),
                intArg(p, "y", AUTO),
                intArg(p, "width", AUTO),
                intArg(p, "height", AUTO));
    }

    static Line buildLine(Map<String, Object> p) {
        int x1 = intArg(p, "x1", 0);
        int y1 = intArg(p, "y1", 0);
        int x2 = intArg(p, "x2", 0);
        int y2 = intArg(p, "y2", 0);
        String pen = stringArg(p, "pen", null);
        if (pen != null && !pen.isEmpty()) {
            return new Line(new Point(x1, y1), new Point(x2, y2), pen.charAt(0));
        }
        return new Line(new Point(x1, y1), new Point(x2, y2));
    }

    static Label buildLabel(Map<String, Object> p) {
        String text = requireString(p, "text", "label");
        if (!p.containsKey("x") && !p.containsKey("y") && !p.containsKey("width")) {
            return new Label(text);
        }
        int x = intArg(p, "x", AUTO);
        int y = intArg(p, "y", AUTO);
        if (p.containsKey("width")) {
            return new Label(text, x, y, intArg(p, "width", AUTO));
        }
        return new Label(text, x, y);
    }

    static Text buildText(Map<String, Object> p) {
        String text = requireString(p, "text", "text");
        if (!p.containsKey("x") && !p.containsKey("y")
                && !p.containsKey("width") && !p.containsKey("height")) {
            return new Text(text);
        }
        return new Text(text,
                intArg(p, "x", AUTO),
                intArg(p, "y", AUTO),
                intArg(p, "width", AUTO),
                intArg(p, "height", AUTO));
    }

    static Dot buildDot(Map<String, Object> p) {
        if (!p.containsKey("x") && !p.containsKey("y")) {
            return new Dot();
        }
        return new Dot(intArg(p, "x", AUTO), intArg(p, "y", AUTO));
    }

    static Circle buildCircle(Map<String, Object> p) {
        if (!p.containsKey("x") && !p.containsKey("y") && !p.containsKey("radius")) {
            return new Circle();
        }
        return new Circle(
                intArg(p, "x", AUTO),
                intArg(p, "y", AUTO),
                intArg(p, "radius", AUTO));
    }

    static Ellipse buildEllipse(Map<String, Object> p) {
        if (p.isEmpty()) {
            return new Ellipse();
        }
        return new Ellipse(
                intArg(p, "x", AUTO),
                intArg(p, "y", AUTO),
                intArg(p, "width", AUTO),
                intArg(p, "height", AUTO));
    }

    static Table buildTable(Map<String, Object> p) {
        int columns = intArg(p, "columns", 0);
        int rows = intArg(p, "rows", 0);
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("'columns' and 'rows' must be positive.");
        }
        if (p.containsKey("x") || p.containsKey("y")
                || p.containsKey("width") || p.containsKey("height")) {
            return new Table(
                    intArg(p, "x", 0),
                    intArg(p, "y", 0),
                    intArg(p, "width", 0),
                    intArg(p, "height", 0),
                    columns, rows);
        }
        return new Table(columns, rows);
    }

    static Plot buildPlot(Map<String, Object> p) {
        Object rawPts = p.get("points");
        if (!(rawPts instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new IllegalArgumentException("'points' must be a non-empty list of [x, y] pairs.");
        }
        List<IPlotPoint> plotPoints = new ArrayList<>(rawList.size());
        for (Object o : rawList) {
            if (!(o instanceof List<?> pair) || pair.size() < 2) {
                throw new IllegalArgumentException("Each plot point must be a [x, y] list.");
            }
            plotPoints.add(new PlotPoint(
                    ((Number) pair.get(0)).doubleValue(),
                    ((Number) pair.get(1)).doubleValue()));
        }
        Region region = new Region(
                intArg(p, "x", 0),
                intArg(p, "y", 0),
                intArg(p, "width", 0),
                intArg(p, "height", 0));
        if (region.getWidth() <= 0 || region.getHeight() <= 0) {
            throw new IllegalArgumentException("'width' and 'height' must be positive.");
        }
        return new Plot(plotPoints, region);
    }

    /**
     * Tiny dispatch table — one line per element kind. Returns {@code null}
     * for unknown types so {@link AsciiSceneFactory} can produce a single
     * uniform error message at the dispatch site.
     */
    static IElement dispatch(String type, Map<String, Object> params) {
        return switch (type) {
            case "rectangle" -> buildRectangle(params);
            case "line"      -> buildLine(params);
            case "label"     -> buildLabel(params);
            case "text"      -> buildText(params);
            case "dot"       -> buildDot(params);
            case "circle"    -> buildCircle(params);
            case "ellipse"   -> buildEllipse(params);
            case "table"     -> buildTable(params);
            case "plot"      -> buildPlot(params);
            default          -> null;
        };
    }
}
