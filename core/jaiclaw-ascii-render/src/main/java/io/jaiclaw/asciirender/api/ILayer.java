package io.jaiclaw.asciirender.api;

import java.util.List;

/**
 * A layer is a fixed-region container for {@link IElement}s. Layers are
 * drawn in the order they are added to the context; later layers
 * overdraw earlier ones.
 *
 * <p>If a layer is {@linkplain #isOpacity() opaque}, drawing into it
 * with a {@code \0} (null) glyph erases content beneath it. The
 * default ({@code false}) lets unset glyphs pass through.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.api.ILayer}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public interface ILayer {

    IRegion getRegion();

    List<IElement> getElements();

    boolean isOpacity();
}
