package io.jaiclaw.asciirender.api;

/**
 * Top-level renderer. Construct a context via
 * {@link #newBuilder()}, then turn it into a canvas via
 * {@link #render(IContext)}.
 *
 * <p>The upstream library had an experimental {@code PseudoCanvas} flag
 * that routed drawing through an AWT-backed canvas for accurate font
 * metrics; that path is intentionally not ported here because it
 * pulled {@code java.awt.*} into a "pure Java" library.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.api.IRender}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public interface IRender {

    IContextBuilder newBuilder();

    ICanvas render(IContext context);
}
