package io.jaiclaw.asciirender.element.plot;

/**
 * Immutable (x, y) sample.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.plot.misc.PlotPoint},
 * original author indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public record PlotPoint(double x, double y) implements IPlotPoint {

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }
}
