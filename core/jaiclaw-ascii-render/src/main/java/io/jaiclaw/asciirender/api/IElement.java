package io.jaiclaw.asciirender.api;

/**
 * Graphical element drawable on a canvas — a primitive (Line, Rectangle,
 * Circle, ...) or a composite (Plot, Table, ...). Implementations are
 * stateless per render call; the canvas + context are passed in.
 *
 * <p>Adopters can implement custom shapes by implementing this
 * interface; the contract is deliberately small and open (not sealed).
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.api.IElement}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public interface IElement {

    /**
     * Draw this element on {@code canvas}. Return the anchor point for
     * this element in the relative coordinates of its layer, or
     * {@code null} if the element drew nothing (e.g. because it was
     * fully clipped).
     */
    IPoint draw(ICanvas canvas, IContext context);
}
