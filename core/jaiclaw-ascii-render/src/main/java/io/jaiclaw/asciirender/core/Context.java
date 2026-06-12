package io.jaiclaw.asciirender.core;

import io.jaiclaw.asciirender.api.IContext;
import io.jaiclaw.asciirender.api.IElement;
import io.jaiclaw.asciirender.api.ILayer;
import io.jaiclaw.asciirender.api.IPoint;
import io.jaiclaw.asciirender.api.IRegion;
import io.jaiclaw.asciirender.api.ITypedIdentified;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link IContext} implementation. Construction is
 * package-private — created indirectly via {@link ContextBuilder}.
 *
 * <p>Holds three indices for {@code lookup*} performance:
 * <ul>
 *   <li>{@code layersByElement}: element -> layers containing it</li>
 *   <li>{@code elementsByClass}: concrete element class -> elements</li>
 *   <li>{@code identifiedByType}: typed-id group class -> id -> element</li>
 * </ul>
 *
 * <p>Apache 2.0 — ported from
 * {@code com.indvd00m.ascii.render.Context}, original author indvd00m
 * {@code <gotoindvdum at gmail dot com>}. Generic types tightened and
 * unchecked casts narrowed; no behavioural changes.
 */
public class Context implements IContext {

    private int width;
    private int height;
    private final List<ILayer> layers = new ArrayList<>();

    private final Map<IElement, Set<ILayer>> layersByElement = new HashMap<>();
    private final Map<Class<? extends IElement>, Set<IElement>> elementsByClass = new HashMap<>();
    private final Map<Class<?>, Map<Integer, ITypedIdentified<?>>> identifiedByType = new HashMap<>();

    Context() {}

    void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    void addLayer(ILayer layer) {
        layers.add(layer);
        for (IElement element : layer.getElements()) {
            indexElement(element, layer);
        }
    }

    void indexElement(IElement element, ILayer layer) {
        layersByElement.computeIfAbsent(element, k -> new LinkedHashSet<>()).add(layer);
        elementsByClass.computeIfAbsent(element.getClass(), k -> new LinkedHashSet<>()).add(element);
        if (element instanceof ITypedIdentified<?> ti) {
            Class<?> group = ti.getType();
            identifiedByType.computeIfAbsent(group, k -> new HashMap<>())
                    .put(ti.getTypedId(), ti);
        }
    }

    // ── IContext: dimensions ─────────────────────────────────────

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public List<ILayer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    // ── IContext: element lookup ─────────────────────────────────

    @Override
    public <E extends IElement> E lookup(Class<E> clazz) {
        return lookup(clazz, true);
    }

    @Override
    public <E extends IElement> E lookup(Class<E> clazz, boolean includeSuccessors) {
        List<E> elements = lookupAll(clazz, includeSuccessors);
        return elements.isEmpty() ? null : elements.get(0);
    }

    @Override
    public <E extends IElement> List<E> lookupAll(Class<E> clazz) {
        return lookupAll(clazz, true);
    }

    @Override
    public <E extends IElement> List<E> lookupAll(Class<E> clazz, boolean includeSuccessors) {
        return collectByClass(clazz, includeSuccessors, null);
    }

    @Override
    public <E extends IElement> E lookup(Class<E> clazz, ILayer layer) {
        return lookup(clazz, true, layer);
    }

    @Override
    public <E extends IElement> E lookup(Class<E> clazz, boolean includeSuccessors, ILayer layer) {
        List<E> elements = lookupAll(clazz, includeSuccessors, layer);
        return elements.isEmpty() ? null : elements.get(0);
    }

    @Override
    public <E extends IElement> List<E> lookupAll(Class<E> clazz, ILayer layer) {
        return lookupAll(clazz, true, layer);
    }

    @Override
    public <E extends IElement> List<E> lookupAll(Class<E> clazz, boolean includeSuccessors, ILayer layer) {
        return collectByClass(clazz, includeSuccessors, layer);
    }

    @SuppressWarnings("unchecked")
    private <E extends IElement> List<E> collectByClass(Class<E> clazz,
                                                        boolean includeSuccessors,
                                                        ILayer layer) {
        LinkedHashSet<IElement> set = new LinkedHashSet<>();

        Set<IElement> exactMatches = elementsByClass.get(clazz);
        if (exactMatches != null) {
            for (IElement e : exactMatches) {
                if (includeSuccessors || clazz.equals(e.getClass())) {
                    if (layer == null || isInLayer(e, layer)) {
                        set.add(e);
                    }
                }
            }
        }
        if (includeSuccessors) {
            for (Class<? extends IElement> nextClazz : elementsByClass.keySet()) {
                if (clazz.isAssignableFrom(nextClazz) && !clazz.equals(nextClazz)) {
                    for (IElement e : elementsByClass.get(nextClazz)) {
                        if (layer == null || isInLayer(e, layer)) {
                            set.add(e);
                        }
                    }
                }
            }
        }
        return (List<E>) new ArrayList<>(set);
    }

    private boolean isInLayer(IElement element, ILayer layer) {
        Set<ILayer> elementLayers = layersByElement.get(element);
        return elementLayers != null && elementLayers.contains(layer);
    }

    @Override
    public ILayer lookupLayer(IElement element) {
        Set<ILayer> elementLayers = layersByElement.get(element);
        if (elementLayers == null || elementLayers.isEmpty()) {
            return null;
        }
        return elementLayers.iterator().next();
    }

    @Override
    public List<ILayer> lookupLayers(IElement element) {
        Set<ILayer> elementLayers = layersByElement.get(element);
        return elementLayers == null ? new ArrayList<>() : new ArrayList<>(elementLayers);
    }

    // ── IContext: typed-id lookup ────────────────────────────────

    @Override
    public <T extends ITypedIdentified<T>> T lookupTyped(Class<T> type, int typedId) {
        return lookupTyped(type, typedId, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ITypedIdentified<T>> T lookupTyped(Class<T> type, int typedId,
                                                          boolean includeSuccessors) {
        Map<Integer, ITypedIdentified<?>> exactMap = identifiedByType.get(type);
        if (exactMap != null && exactMap.containsKey(typedId)) {
            ITypedIdentified<?> ti = exactMap.get(typedId);
            if (includeSuccessors || type.equals(ti.getClass())) {
                return (T) ti;
            }
        }
        if (includeSuccessors) {
            for (Class<?> nextType : identifiedByType.keySet()) {
                if (type.isAssignableFrom(nextType) && !type.equals(nextType)) {
                    Map<Integer, ITypedIdentified<?>> map = identifiedByType.get(nextType);
                    if (map != null && map.containsKey(typedId)) {
                        return (T) map.get(typedId);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public <T extends ITypedIdentified<T>> List<T> lookupTyped(Class<T> type) {
        return lookupTyped(type, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ITypedIdentified<T>> List<T> lookupTyped(Class<T> type,
                                                                boolean includeSuccessors) {
        LinkedHashSet<ITypedIdentified<?>> set = new LinkedHashSet<>();

        Map<Integer, ITypedIdentified<?>> exactMap = identifiedByType.get(type);
        if (exactMap != null) {
            for (ITypedIdentified<?> ti : exactMap.values()) {
                if (includeSuccessors || type.equals(ti.getClass())) {
                    set.add(ti);
                }
            }
        }
        if (includeSuccessors) {
            for (Class<?> nextType : identifiedByType.keySet()) {
                if (type.isAssignableFrom(nextType) && !type.equals(nextType)) {
                    Map<Integer, ITypedIdentified<?>> map = identifiedByType.get(nextType);
                    if (map != null) {
                        set.addAll(map.values());
                    }
                }
            }
        }
        List<T> result = new ArrayList<>();
        for (ITypedIdentified<?> ti : set) {
            result.add((T) ti);
        }
        return result;
    }

    // ── IContext: misc ───────────────────────────────────────────

    @Override
    public boolean contains(IElement element) {
        return layersByElement.containsKey(element);
    }

    @Override
    public IPoint transform(IPoint point, ILayer source, ILayer target) {
        IRegion sourceRegion = source.getRegion();
        IRegion targetRegion = target.getRegion();
        int x = sourceRegion.getX() - targetRegion.getX() + point.getX();
        int y = sourceRegion.getY() - targetRegion.getY() + point.getY();
        return new Point(x, y);
    }

    @Override
    public IPoint transform(IPoint point, IElement source, IElement target) {
        ILayer sourceLayer = lookupLayer(source);
        ILayer targetLayer = lookupLayer(target);
        return transform(point, sourceLayer, targetLayer);
    }
}
