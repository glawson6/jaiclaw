package io.jaiclaw.asciirender.element.plot;

import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.api.IRegion;
import io.jaiclaw.asciirender.api.ITypedIdentified;
import io.jaiclaw.asciirender.core.Point;

import java.util.List;

/**
 * Base for plot elements. Each instance is identified amongst its
 * peers (same concrete type, same context) by a typed id derived from
 * its anchor point and points hash.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.elements.plot.AbstractPlotObject},
 * original author indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public abstract class AbstractPlotObject<P extends AbstractPlotObject<P>>
        implements IElement, ITypedIdentified<P> {

    protected final int typedId;
    protected final List<IPlotPoint> points;
    protected final PlotPoints plotPoints;
    protected final IRegion region;
    protected final IPoint anchorPoint;

    protected AbstractPlotObject(List<IPlotPoint> points, IRegion region) {
        this.points = points;
        this.plotPoints = new PlotPoints(points);
        this.region = region;
        this.anchorPoint = new Point(region.getX(), region.getY() + region.getHeight() - 1);
        this.typedId = generateTypedId();
    }

    private int generateTypedId() {
        final int prime = 31;
        int result = 1;
        result = prime * result + anchorPoint.getX();
        result = prime * result + anchorPoint.getY();
        result = prime * result + (points == null ? 0 : points.hashCode());
        return result;
    }

    public List<IPlotPoint> getPoints() {
        return points;
    }

    public IRegion getRegion() {
        return region;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<P> getType() {
        return (Class<P>) getClass();
    }

    @Override
    public int getTypedId() {
        return typedId;
    }
}
