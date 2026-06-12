package io.jaiclaw.asciirender.api;

import java.util.List;

/**
 * Fluent builder for an {@link IContext}. Calls chain.
 *
 * <p>Typical use:
 * <pre>{@code
 * IContext ctx = render.newBuilder()
 *         .width(80).height(20)
 *         .layer(new Region(0, 0, 80, 20))
 *           .element(new Rectangle())
 *           .element(new Label("hello", 30, 9))
 *         .build();
 * }</pre>
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.api.IContextBuilder}, original
 * author indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public interface IContextBuilder {

    IContext build();

    IContextBuilder width(int width);

    IContextBuilder height(int height);

    /**
     * Append a layer covering the entire context region
     * {@code (0, 0, width, height)}.
     */
    IContextBuilder layer();

    IContextBuilder layer(IRegion region);

    IContextBuilder layer(int x, int y, int width, int height);

    IContextBuilder layer(IElement... elements);

    IContextBuilder layer(IRegion region, IElement... elements);

    IContextBuilder layer(int x, int y, int width, int height, IElement... elements);

    IContextBuilder layer(List<IElement> elements);

    IContextBuilder layer(IRegion region, List<IElement> elements);

    IContextBuilder layer(int x, int y, int width, int height, List<IElement> elements);

    /**
     * Mark the most-recently-added layer opaque (default false).
     */
    IContextBuilder opacity(boolean opacity);

    /**
     * Append an element to the most-recently-added layer. If no layer
     * has been added yet, one is created covering the full context
     * region.
     */
    IContextBuilder element(IElement element);

    IContextBuilder elements(IElement... elements);

    IContextBuilder elements(List<IElement> elements);
}
