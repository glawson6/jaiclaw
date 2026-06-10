package io.jaiclaw.plugin;

import io.jaiclaw.core.api.Internal;
import io.jaiclaw.core.hook.HookRegistration;
import io.jaiclaw.core.hook.event.HookEvent;
import io.jaiclaw.core.tenant.TenantContextPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executes hook handlers for a given typed event.
 *
 * <p>0.8.0 hard-break: dispatch is keyed by the event's runtime class rather
 * than the pre-0.8.0 {@code HookName} enum. See {@code docs/MIGRATION-0.8.md}
 * § P3.1.
 *
 * <ul>
 *   <li>Void hooks (handler returns null): run in parallel via virtual threads.</li>
 *   <li>Modifying hooks (handler returns a replacement event): run sequentially
 *       in priority order, each receiving the previous handler's result.</li>
 * </ul>
 */
@Internal
public class HookRunner {

    private static final Logger log = LoggerFactory.getLogger(HookRunner.class);

    private final PluginRegistry pluginRegistry;

    public HookRunner(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * Fire a void hook — handlers run in parallel via virtual threads.
     * Exceptions in individual handlers are logged but don't block others.
     */
    @SuppressWarnings("unchecked")
    public <E extends HookEvent> void fireVoid(E event) {
        Class<? extends HookEvent> eventType = event.getClass();
        var handlers = getHandlers(eventType);
        if (handlers.isEmpty()) return;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = handlers.stream()
                    .map(h -> CompletableFuture.runAsync(TenantContextPropagator.wrap(() -> {
                        try {
                            ((HookRegistration<E>) h).handler().handle(event);
                        } catch (Exception e) {
                            log.warn("Hook handler {} for {} failed: {}",
                                    h.pluginId(), eventType.getSimpleName(), e.getMessage(), e);
                        }
                    }), executor))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();
        }
    }

    /**
     * Fire a modifying hook — handlers run sequentially in priority order.
     * Each handler receives the result of the previous one.
     *
     * @return the final modified event, or the original if no handlers exist
     */
    @SuppressWarnings("unchecked")
    public <E extends HookEvent> E fireModifying(E event) {
        Class<? extends HookEvent> eventType = event.getClass();
        var handlers = getHandlers(eventType);
        if (handlers.isEmpty()) return event;

        E current = event;
        for (var h : handlers) {
            try {
                E result = ((HookRegistration<E>) h).handler().handle(current);
                if (result != null) {
                    current = result;
                }
            } catch (Exception e) {
                log.warn("Modifying hook handler {} for {} failed: {}",
                        h.pluginId(), eventType.getSimpleName(), e.getMessage(), e);
            }
        }
        return current;
    }

    /**
     * Check if any handlers are registered for the given event type.
     */
    public <E extends HookEvent> boolean hasHandlers(Class<E> eventType) {
        return !getHandlers(eventType).isEmpty();
    }

    private List<HookRegistration<? extends HookEvent>> getHandlers(Class<? extends HookEvent> eventType) {
        return pluginRegistry.hooks().stream()
                .filter(h -> h.eventType().equals(eventType))
                .sorted(Comparator.comparingInt(HookRegistration::priority))
                .toList();
    }
}
