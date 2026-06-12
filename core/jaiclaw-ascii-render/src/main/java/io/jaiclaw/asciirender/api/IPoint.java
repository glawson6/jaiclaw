package io.jaiclaw.asciirender.api;

/**
 * A 2D point in canvas coordinates.
 *
 * <p>The reference upstream type was a class with {@code getX()} /
 * {@code getY()}. Here it's kept as an interface so the canonical
 * implementation can be a Java 21 record while callers who already
 * implement custom point types still work.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.api.IPoint}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public interface IPoint {

    int getX();

    int getY();
}
