package io.jaiclaw.asciirender.element.plot;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.api.IRegion;

import java.util.List;

/**
 * X and Y axes for a plot, drawn as a unicode L-shape inside
 * {@code region}. If a sibling {@link AxisLabels} of the same typed
 * id is present, the axis is inset to leave room for the y-labels
 * and tick marks are emitted at each label's anchor point.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.plot.Axis}, original
 * author indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Axis extends AbstractPlotObject<Axis> {

    public Axis(List<IPlotPoint> points, IRegion region) {
        super(points, region);
    }

    @Override
    public IPoint draw(ICanvas canvas, IContext context) {
        int width = region.getWidth();
        int height = region.getHeight();
        int startX = region.getX();
        int startY = region.getY();
        int lastY = startY + height - 1;

        AxisLabels labels = context.lookupTyped(AxisLabels.class, getTypedId());
        if (labels != null) {
            startX += labels.getLabelsYWidth();
            width -= labels.getLabelsYWidth();
            lastY -= 1;
            height -= 1;
        }

        canvas.draw(startX, startY, "│\n", height);
        canvas.draw(startX + 1, lastY, "─", width - 1);
        canvas.draw(startX, lastY, "└");

        if (labels != null) {
            for (AxisLabel label : labels.getLabels()) {
                IPoint labelPoint = label.getAnchorPoint();
                IPoint point = context.transform(labelPoint, labels, this);
                if (label.getAxisType() == AxisType.X) {
                    canvas.draw(point.getX(), lastY, "┼");
                } else if (label.getAxisType() == AxisType.Y) {
                    canvas.draw(startX, point.getY(), "┼");
                }
            }
        }
        return anchorPoint;
    }
}
