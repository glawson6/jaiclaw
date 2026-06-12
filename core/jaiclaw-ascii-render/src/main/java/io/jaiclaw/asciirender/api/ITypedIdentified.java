package io.jaiclaw.asciirender.api;

/**
 * Optional mixin for elements that compose graphical objects out of
 * several mutually-aware sub-elements (e.g. a chart with axis + plot +
 * labels). Elements implementing this contract can look each other up
 * via {@link IContext#lookupTyped} by id.
 *
 * <p>Implementations must guarantee {@link #getTypedId()} is unique
 * amongst all objects with the same {@link #getType()} (including
 * successors) in the same context.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.api.ITypedIdentified}, original
 * author indvd00m {@code <gotoindvdum at gmail dot com>}.
 *
 * @param <T> the typed-identification group
 */
public interface ITypedIdentified<T> {

    Class<T> getType();

    int getTypedId();
}
