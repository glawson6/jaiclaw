package io.jaiclaw.asciirender.element.plot;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.api.IRegion;
import io.jaiclaw.asciirender.core.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * Tick labels for an {@link Axis}. Generates {@code countX} x-labels
 * and {@code countY} y-labels on construction based on the linked
 * data range, then draws each as a {@link AxisLabel} sub-element.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.plot.AxisLabels}, original
 * author indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class AxisLabels extends AbstractPlotObject<AxisLabels> {

    private static final String DEFAULT_DECIMAL_FORMAT = "%1$,.2f";

    private final int countX;
    private final int countY;
    private final String decimalFractionsLabelsFormat;
    private final List<AxisLabel> labels = new ArrayList<>();
    private int labelsYWidth;

    public AxisLabels(List<IPlotPoint> points, IRegion region) {
        this(points, region, 5, 5, DEFAULT_DECIMAL_FORMAT);
    }

    public AxisLabels(List<IPlotPoint> points, IRegion region, String decimalFractionsLabelsFormat) {
        this(points, region, 5, 5, decimalFractionsLabelsFormat);
    }

    public AxisLabels(List<IPlotPoint> points, IRegion region, int countX, int countY) {
        this(points, region, countX, countY, DEFAULT_DECIMAL_FORMAT);
    }

    public AxisLabels(List<IPlotPoint> points, IRegion region, int countX, int countY,
                      String decimalFractionsLabelsFormat) {
        super(points, region);
        this.countX = countX;
        this.countY = countY;
        this.decimalFractionsLabelsFormat = decimalFractionsLabelsFormat;
        generateLabels();
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        for (AxisLabel label : labels) {
            label.draw(canvas, context);
        }
        return anchorPoint;
    }

    private void generateLabels() {
        labels.clear();

        List<String> textsX = createTexts(plotPoints.getMinX(), plotPoints.getDiffX(), countX);
        List<String> textsY = createTexts(plotPoints.getMinY(), plotPoints.getDiffY(), countY);
        labelsYWidth = maxLength(textsY);

        int width = region.getWidth();
        int height = region.getHeight();
        int startX = region.getX();
        int startY = region.getY();
        int lastX = startX + width - 1;
        int lastY = startY + height - 1;

        // y labels
        Deque<String> yTexts = new LinkedList<>(textsY);
        String topText = yTexts.pollLast();
        String bottomText = yTexts.pollFirst();
        double yStep = (double) (height - 2) / (double) (yTexts.size() + 1);
        for (int y = startY; y < lastY; y++) {
            String text = null;
            if (y == startY) {
                text = topText;
            } else if (y == lastY - 1) {
                text = bottomText;
            } else if (y % yStep < 1) {
                text = yTexts.pollLast();
            }
            if (text != null) {
                text = String.format("%" + labelsYWidth + "s", text);
                labels.add(new AxisLabel(text, startX, y, AxisType.Y, new Point(startX + labelsYWidth, y)));
            }
        }

        // x labels
        Deque<String> xTexts = new LinkedList<>(textsX);
        String leftText = xTexts.pollFirst();
        String rightText = xTexts.pollLast();
        double xStep = (double) (width - labelsYWidth) / (double) (xTexts.size() + 1);

        String text = xTexts.pollFirst();
        int num = 0;
        while (text != null) {
            num++;
            int position = startX + labelsYWidth + (int) (xStep * num);
            int start = position - text.length() / 2;
            labels.add(new AxisLabel(text, start, lastY, AxisType.X, new Point(position, lastY)));
            text = xTexts.pollFirst();
        }

        labels.add(new AxisLabel(leftText, startX + labelsYWidth, lastY, AxisType.X,
                new Point(startX + labelsYWidth, lastY)));
        labels.add(new AxisLabel(rightText, lastX - rightText.length() + 1, lastY, AxisType.X,
                new Point(lastX, lastY)));
    }

    private List<String> createTexts(double minValue, double diffValues, int count) {
        List<String> texts = new ArrayList<>(count);
        double step = diffValues / (count - 1);
        for (int i = 0; i < count; i++) {
            double value = i * step + minValue;
            texts.add(format(value, step));
        }
        return texts;
    }

    private String format(double value, double step) {
        if (step < 10d) {
            return String.format(decimalFractionsLabelsFormat, value);
        }
        return String.format("%,d", (int) value);
    }

    private static int maxLength(List<String> strings) {
        int max = 0;
        for (String s : strings) {
            if (s.length() > max) {
                max = s.length();
            }
        }
        return max;
    }

    public List<AxisLabel> getLabels() {
        return Collections.unmodifiableList(labels);
    }

    public int getCountX() {
        return countX;
    }

    public int getCountY() {
        return countY;
    }

    public int getLabelsYWidth() {
        return labelsYWidth;
    }
}
