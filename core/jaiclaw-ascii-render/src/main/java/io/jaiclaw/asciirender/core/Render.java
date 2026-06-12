package io.jaiclaw.asciirender.core;

import io.jaiclaw.asciirender.api.ICanvas;
import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IContextBuilder;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.ILayer;
import io.jaiclaw.asciirender.api.IRegion;
import io.jaiclaw.asciirender.api.IRender;

/**
 * Reference {@link IRender}. For each layer in the context, draws the
 * layer's elements onto a private layer canvas, then composes that
 * canvas onto the main canvas honouring
 * {@link ILayer#isOpacity()}.
 *
 * <p>Notes vs. upstream:
 * <ul>
 *   <li>The {@code pseudoCanvas} flag and the AWT-backed
 *       {@code PseudoCanvas} alternative are not ported — they
 *       coupled the library to {@code java.awt.*} for marginal
 *       benefit. Adopters who need that behaviour can subclass
 *       this class.</li>
 *   <li>Convenience static {@code builder()} returns a fresh
 *       {@link ContextBuilder} so callers don't need to instantiate
 *       a Render just to start a scene.</li>
 * </ul>
 *
 * <p>Apache 2.0 — ported from {@code com.indvd00m.ascii.render.Render},
 * original author indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public class Render implements IRender {

    /**
     * Convenience entry point — equivalent to
     * {@code new Render().newBuilder()}.
     */
    public static IContextBuilder builder() {
        return ContextBuilder.newBuilder();
    }

    @Override
    public IContextBuilder newBuilder() {
        return ContextBuilder.newBuilder();
    }

    @Override
    public ICanvas render(IContext context) {
        ICanvas canvas = new Canvas(context.getWidth(), context.getHeight());
        for (ILayer layer : context.getLayers()) {
            IRegion region = layer.getRegion();
            ICanvas layerCanvas = new Canvas(region.getWidth(), region.getHeight());
            for (IElement element : layer.getElements()) {
                element.draw(layerCanvas, context);
            }
            drawOver(canvas, layerCanvas, layer, region);
        }
        return canvas;
    }

    /**
     * Compose {@code layerCanvas} onto {@code mainCanvas} at the
     * layer's region. If the layer is opaque, every glyph (including
     * {@code \0}) overwrites; otherwise only drawn glyphs do.
     */
    protected void drawOver(ICanvas mainCanvas, ICanvas layerCanvas,
                            ILayer layer, IRegion region) {
        boolean opacity = layer.isOpacity();
        int x0 = region.getX();
        int y0 = region.getY();
        int width = region.getWidth();
        int height = region.getHeight();
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                if (opacity || layerCanvas.isCharDrawed(dx, dy)) {
                    mainCanvas.draw(x0 + dx, y0 + dy, layerCanvas.getChar(dx, dy));
                }
            }
        }
    }
}
