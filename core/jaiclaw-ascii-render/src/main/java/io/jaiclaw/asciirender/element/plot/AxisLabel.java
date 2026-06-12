package io.jaiclaw.asciirender.element.plot;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.element.Label;

/**
 * A single tick label on an {@link Axis}. Carries the axis it
 * belongs to and an anchor point in the parent axis-labels coordinate
 * system so the axis can position its tick marks against it.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.plot.misc.AxisLabel},
 * original author indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class AxisLabel extends Label {

    private final AxisType axisType;
    private final IPoint anchorPoint;

    public AxisLabel(String text, int x, int y, AxisType axisType, IPoint anchorPoint) {
        super(text, x, y, text.length());
        this.axisType = axisType;
        this.anchorPoint = anchorPoint;
    }

    public AxisType getAxisType() {
        return axisType;
    }

    public IPoint getAnchorPoint() {
        return anchorPoint;
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        super.draw(canvas, context);
        return anchorPoint;
    }
}
