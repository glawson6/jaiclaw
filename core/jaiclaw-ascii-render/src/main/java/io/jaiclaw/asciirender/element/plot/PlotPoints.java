package io.jaiclaw.asciirender.element.plot;

import java.util.ArrayList;
import java.util.List;

/**
 * Derived bounds (min/max/range) over a list of {@link IPlotPoint}s plus
 * the ability to project them into a target rectangle of size
 * {@code maxX × maxY}.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.plot.misc.PlotPoints},
 * original author indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public final class PlotPoints {

    private final List<IPlotPoint> points;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;
    private final double diffX;
    private final double diffY;

    public PlotPoints(List<IPlotPoint> points) {
        this.points = points;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (IPlotPoint p : points) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getY() > maxY) maxY = p.getY();
        }
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.diffX = maxX - minX;
        this.diffY = maxY - minY;
    }

    public List<IPlotPoint> getPoints() {
        return points;
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getDiffX() {
        return diffX;
    }

    public double getDiffY() {
        return diffY;
    }

    /**
     * Linearly project all points into a {@code [0..maxX] × [0..maxY]}
     * rectangle. If the data is degenerate along an axis (all values
     * equal), every point lands at the origin on that axis to avoid a
     * NaN from a divide-by-zero.
     */
    public List<IPlotPoint> normalize(double maxX, double maxY) {
        List<IPlotPoint> normalized = new ArrayList<>(points.size());
        for (IPlotPoint p : points) {
            double relX = diffX == 0d ? 0d : (p.getX() - minX) / diffX;
            double relY = diffY == 0d ? 0d : (p.getY() - minY) / diffY;
            normalized.add(new PlotPoint(relX * maxX, relY * maxY));
        }
        return normalized;
    }
}
