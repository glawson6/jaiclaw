package io.jaiclaw.asciirender.api;

import java.util.List;

/**
 * Snapshot of a scene: width, height, and a list of layers in draw
 * order. Built via {@link IContextBuilder}; consumed by
 * {@link IRender#render(IContext)}.
 *
 * <p>Lookup helpers ({@link #lookup}, {@link #lookupAll},
 * {@link #lookupTyped}) let composite elements find their
 * collaborators without explicit references — used heavily by Plot,
 * Axis, and Table internals.
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.api.IContext}, original author
 * indvd00m {@code <gotoindvdum at gmail dot com>}.
 */
public interface IContext {

    int getWidth();

    int getHeight();

    List<ILayer> getLayers();

    /**
     * First element assignable to {@code clazz} (or its successors).
     * Returns {@code null} when nothing matches.
     */
    <E extends IElement> E lookup(Class<E> clazz);

    /**
     * First element assignable to {@code clazz}. If
     * {@code includeSuccessors} is false, only exact-class matches
     * count.
     */
    <E extends IElement> E lookup(Class<E> clazz, boolean includeSuccessors);

    /**
     * Every element assignable to {@code clazz} (or its successors).
     */
    <E extends IElement> List<E> lookupAll(Class<E> clazz);

    <E extends IElement> List<E> lookupAll(Class<E> clazz, boolean includeSuccessors);

    <E extends IElement> E lookup(Class<E> clazz, ILayer layer);

    <E extends IElement> E lookup(Class<E> clazz, boolean includeSuccessors, ILayer layer);

    <E extends IElement> List<E> lookupAll(Class<E> clazz, ILayer layer);

    <E extends IElement> List<E> lookupAll(Class<E> clazz, boolean includeSuccessors, ILayer layer);

    ILayer lookupLayer(IElement element);

    List<ILayer> lookupLayers(IElement element);

    /**
     * Look up an element by its {@link ITypedIdentified#getTypedId()}.
     * See {@link ITypedIdentified} for the contract.
     */
    <T extends ITypedIdentified<T>> T lookupTyped(Class<T> type, int typedId);

    <T extends ITypedIdentified<T>> T lookupTyped(Class<T> type, int typedId, boolean includeSuccessors);

    <T extends ITypedIdentified<T>> List<T> lookupTyped(Class<T> type);

    <T extends ITypedIdentified<T>> List<T> lookupTyped(Class<T> type, boolean includeSuccessors);

    /**
     * True if this context contains {@code element}.
     */
    boolean contains(IElement element);

    /**
     * Transform a point from one layer's coordinate system to
     * another's.
     */
    IPoint transform(IPoint point, ILayer source, ILayer target);

    /**
     * Transform a point from one element's coordinate system to
     * another's.
     */
    IPoint transform(IPoint point, IElement source, IElement target);
}
