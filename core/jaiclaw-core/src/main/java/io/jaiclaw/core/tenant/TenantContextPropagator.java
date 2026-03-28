package io.jaiclaw.core.tenant;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Captures and restores {@link TenantContext} across async boundaries.
 * <p>
 * Since {@link TenantContextHolder} is {@link ThreadLocal}-based, tenant context
 * is lost when work is dispatched to {@code CompletableFuture.supplyAsync()},
 * virtual threads, or any other executor. This utility captures the calling thread's
 * context and restores it in the target thread.
 * <p>
 * No-op when {@link TenantContextHolder#get()} returns null (zero overhead in SINGLE mode).
 *
 * <pre>
 * // Wrap a Runnable
 * executor.execute(TenantContextPropagator.wrap(() -&gt; doWork()));
 *
 * // Wrap a Supplier for CompletableFuture
 * CompletableFuture.supplyAsync(TenantContextPropagator.wrap(() -&gt; compute()));
 * </pre>
 */
public final class TenantContextPropagator {

    private TenantContextPropagator() {}

    /**
     * Wraps a {@link Runnable} to propagate the current thread's tenant context.
     */
    public static Runnable wrap(Runnable task) {
        TenantContext captured = TenantContextHolder.get();
        if (captured == null) return task;
        return () -> {
            TenantContextHolder.set(captured);
            try {
                task.run();
            } finally {
                TenantContextHolder.clear();
            }
        };
    }

    /**
     * Wraps a {@link Supplier} to propagate the current thread's tenant context.
     * Use with {@code CompletableFuture.supplyAsync()}.
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier) {
        TenantContext captured = TenantContextHolder.get();
        if (captured == null) return supplier;
        return () -> {
            TenantContextHolder.set(captured);
            try {
                return supplier.get();
            } finally {
                TenantContextHolder.clear();
            }
        };
    }

    /**
     * Wraps an {@link Executor} to propagate tenant context to all submitted tasks.
     */
    public static Executor wrap(Executor executor) {
        return command -> executor.execute(wrap(command));
    }
}
