package io.jaiclaw.asciirender.element.plot;

/**
 * A single (x, y) sample used by plot elements.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.plot.api.IPlotPoint},
 * original author indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public interface IPlotPoint {

    double getX();

    double getY();
}
