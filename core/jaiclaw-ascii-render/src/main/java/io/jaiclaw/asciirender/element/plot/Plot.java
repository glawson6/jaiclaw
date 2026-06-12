package io.jaiclaw.asciirender.element.plot;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.api.IRegion;

import java.util.List;

/**
 * Scatter plot of {@link IPlotPoint}s rendered inside {@code region}.
 *
 * <p>If a sibling {@link AxisLabels} or {@link Axis} of the same typed
 * id is in the context, the plot area is inset to leave room for them.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.plot.Plot}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Plot extends AbstractPlotObject<Plot> {

    public Plot(List<IPlotPoint> points, IRegion region) {
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

        Axis axis = context.lookupTyped(Axis.class, getTypedId());
        if (axis != null) {
            startX += 1;
            width -= 1;
            lastY -= 1;
            height -= 1;
        }

        List<IPlotPoint> normalized = plotPoints.normalize(width - 1, height - 1);
        for (IPlotPoint plotPoint : normalized) {
            int x = (int) (startX + plotPoint.getX());
            int y = (int) (lastY - plotPoint.getY());
            canvas.draw(x, y, "*");
        }
        return anchorPoint;
    }
}
