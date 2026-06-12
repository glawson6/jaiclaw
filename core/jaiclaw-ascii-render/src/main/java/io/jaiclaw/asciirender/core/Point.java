package io.jaiclaw.asciirender.core;

import io.jaiclaw.asciirender.api.IPoint;

/**
 * Canonical {@link IPoint} as a Java 21 record. Equality and
 * {@code toString} come for free.
 *
 * <p>The upstream class carried {@code hashCode} / {@code equals} /
 * {@code toString} boilerplate (~50 lines); the record eliminates all
 * of it. We expose {@code getX()} / {@code getY()} explicitly to
 * satisfy the {@link IPoint} interface (records auto-generate
 * {@code x()} / {@code y()} accessors which don't match).
 *
 * <p>Apache 2.0 — ported and modernized from
 * {@code com.indvd00m.ascii.render.Point}, original author indvd00m
 * {@code <gotoindvdum at gmail dot com>}.
 */
public record Point(int x, int y) implements IPoint {

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }
}
