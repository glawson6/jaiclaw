package io.jaiclaw.asciirender.core;

import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.ILayer;
import io.jaiclaw.asciirender.api.IRegion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default {@link ILayer} implementation. Construction is
 * package-private — layers are created indirectly via
 * {@link ContextBuilder}.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.Layer}, original author indvd00m
 * {@code <gotoindvdum at gmail dot com>}.
 */
public class Layer implements ILayer {

    private final IRegion region;
    private final List<IElement> elements = new ArrayList<>();
    private boolean opacity;

    Layer(IRegion region) {
        this.region = region;
    }

    @Override
    public IRegion getRegion() {
        return region;
    }

    @Override
    public List<IElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    @Override
    public boolean isOpacity() {
        return opacity;
    }

    void addElement(IElement element) {
        elements.add(element);
    }

    void setOpacity(boolean opacity) {
        this.opacity = opacity;
    }
}
