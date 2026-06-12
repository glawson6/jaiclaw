package io.jaiclaw.asciirender.core;

import io.jaiclaw.asciirender.api.IRegion;

/**
 * Canonical {@link IRegion} as a Java 21 record. Validates non-negative
 * {@code width} / {@code height} at construction.
 *
 * <p>Apache 2.0 — ported and modernized from
 * {@code com.indvd00m.ascii.render.Region}, original author indvd00m
 * {@code <gotoindvdum at gmail dot com>}.
 */
public record Region(int x, int y, int width, int height) implements IRegion {

    public Region {
        if (width < 0) {
            throw new IllegalArgumentException("width must be >= 0, was " + width);
        }
        if (height < 0) {
            throw new IllegalArgumentException("height must be >= 0, was " + height);
        }
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }
}
