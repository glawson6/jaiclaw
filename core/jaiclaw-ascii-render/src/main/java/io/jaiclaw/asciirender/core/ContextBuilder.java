package io.jaiclaw.asciirender.core;

import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IContextBuilder;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.IRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Default {@link IContextBuilder} implementation.
 *
 * <p>Modernisation notes vs. upstream
 * {@code com.indvd00m.ascii.render.ContextBuilder}:
 * <ul>
 *   <li>Upstream eagerly maintained three indices (by element, by
 *       class, by typed-id) on the builder AND copied them to the
 *       {@link Context} at build time. Here, indexing happens once
 *       on the {@link Context} when {@link #build()} runs — the
 *       builder just collects layers.</li>
 *   <li>The {@code getAncestors} reflection helper is gone; the
 *       {@link Context} indexes by concrete class and resolves
 *       successor lookups at query time.</li>
 * </ul>
 *
 * <p>Apache 2.0 — ported and refactored from
 * {@code com.indvd00m.ascii.render.ContextBuilder}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class ContextBuilder implements IContextBuilder {

    private int width;
    private int height;
    private final List<Layer> layers = new ArrayList<>();

    public static IContextBuilder newBuilder() {
        return new ContextBuilder();
    }

    @Override
    public IContext build() {
        Context context = new Context();
        context.setSize(width, height);
        for (Layer layer : layers) {
            context.addLayer(layer);
        }
        return context;
    }

    @Override
    public IContextBuilder width(int width) {
        this.width = width;
        return this;
    }

    @Override
    public IContextBuilder height(int height) {
        this.height = height;
        return this;
    }

    private Layer addLayer(IRegion region) {
        Layer layer = new Layer(region);
        layers.add(layer);
        return layer;
    }

    private Layer fullSizeLayer() {
        return addLayer(new Region(0, 0, width, height));
    }

    private Layer mostRecentLayer() {
        if (layers.isEmpty()) {
            return fullSizeLayer();
        }
        return layers.get(layers.size() - 1);
    }

    // ── IContextBuilder: layer(...) overloads ────────────────────

    @Override
    public IContextBuilder layer() {
        fullSizeLayer();
        return this;
    }

    @Override
    public IContextBuilder layer(IRegion region) {
        addLayer(region);
        return this;
    }

    @Override
    public IContextBuilder layer(int x, int y, int width, int height) {
        return layer(new Region(x, y, width, height));
    }

    @Override
    public IContextBuilder layer(IElement... elements) {
        Layer layer = fullSizeLayer();
        addAll(layer, Arrays.asList(elements));
        return this;
    }

    @Override
    public IContextBuilder layer(IRegion region, IElement... elements) {
        Layer layer = addLayer(region);
        addAll(layer, Arrays.asList(elements));
        return this;
    }

    @Override
    public IContextBuilder layer(int x, int y, int width, int height, IElement... elements) {
        return layer(new Region(x, y, width, height), elements);
    }

    @Override
    public IContextBuilder layer(List<IElement> elements) {
        Layer layer = fullSizeLayer();
        addAll(layer, elements);
        return this;
    }

    @Override
    public IContextBuilder layer(IRegion region, List<IElement> elements) {
        Layer layer = addLayer(region);
        addAll(layer, elements);
        return this;
    }

    @Override
    public IContextBuilder layer(int x, int y, int width, int height, List<IElement> elements) {
        return layer(new Region(x, y, width, height), elements);
    }

    // ── IContextBuilder: element / opacity ───────────────────────

    @Override
    public IContextBuilder opacity(boolean opacity) {
        mostRecentLayer().setOpacity(opacity);
        return this;
    }

    @Override
    public IContextBuilder element(IElement element) {
        mostRecentLayer().addElement(element);
        return this;
    }

    @Override
    public IContextBuilder elements(IElement... elements) {
        addAll(mostRecentLayer(), Arrays.asList(elements));
        return this;
    }

    @Override
    public IContextBuilder elements(List<IElement> elements) {
        addAll(mostRecentLayer(), elements);
        return this;
    }

    private void addAll(Layer layer, List<IElement> elements) {
        for (IElement element : elements) {
            layer.addElement(element);
        }
    }
}
