package io.jaiclaw.asciirender.api;

/**
 * A rectangular region in canvas coordinates: {@code (x, y)} is the
 * top-left corner; {@code width} and {@code height} are inclusive.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.api.IRegion}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public interface IRegion {

    int getX();

    int getY();

    int getWidth();

    int getHeight();
}
