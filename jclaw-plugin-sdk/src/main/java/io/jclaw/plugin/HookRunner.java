package io.jclaw.plugin;

import io.jclaw.core.hook.HookName;
import io.jclaw.core.hook.HookRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executes hook handlers for a given hook event.
 * <ul>
 *   <li>Void hooks (no return expected): run in parallel using virtual threads</li>
 *   <li>Modifying hooks (return a value): run sequentially in priority order</li>
 * </ul>
 */
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
    public <E, C> void fireVoid(HookName hookName, E event, C context) {
        var handlers = getHandlers(hookName);
        if (handlers.isEmpty()) return;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = handlers.stream()
                    .map(h -> CompletableFuture.runAsync(() -> {
                        try {
                            ((HookRegistration<E, C>) h).handler().handle(event, context);
                        } catch (Exception e) {
                            log.warn("Hook handler {} for {} failed: {}",
                                    h.pluginId(), hookName, e.getMessage(), e);
                        }
                    }, executor))
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
    public <E, C> E fireModifying(HookName hookName, E event, C context) {
        var handlers = getHandlers(hookName);
        if (handlers.isEmpty()) return event;

        E current = event;
        for (var h : handlers) {
            try {
                Object result = ((HookRegistration<E, C>) h).handler().handle(current, context);
                if (result != null) {
                    current = (E) result;
                }
            } catch (Exception e) {
                log.warn("Modifying hook handler {} for {} failed: {}",
                        h.pluginId(), hookName, e.getMessage(), e);
            }
        }
        return current;
    }

    /**
     * Check if any handlers are registered for the given hook.
     */
    public boolean hasHandlers(HookName hookName) {
        return !getHandlers(hookName).isEmpty();
    }

    private List<HookRegistration<?, ?>> getHandlers(HookName hookName) {
        return pluginRegistry.hooks().stream()
                .filter(h -> h.hookName() == hookName)
                .sorted(Comparator.comparingInt(HookRegistration::priority))
                .toList();
    }
}
