package io.jaiclaw.agentmind.tendencies.executor;

import io.jaiclaw.core.tenant.TenantContextPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-(tenant, user) single-thread executor over virtual threads. Same-key
 * submissions are processed in submission order (so dialectic writes for
 * the same user don't reorder); different keys run in parallel.
 *
 * <p>Each stripe is a single-threaded executor with a bounded queue
 * (default 4 entries). When the queue is full a new submission causes the
 * oldest queued task to be dropped — the dialectic pipeline favours the
 * freshest user signal and the dropped task would have been superseded by
 * the next pass anyway.
 *
 * <p>Every submission is wrapped via {@link TenantContextPropagator} so
 * the tenant context that submitted the work is restored inside the
 * worker thread (analysis §5.8 multi-tenant async propagation rule).
 *
 * <p>Plan §8 task 3.7.
 */
public class StripedDialecticExecutor {

    private static final Logger log = LoggerFactory.getLogger(StripedDialecticExecutor.class);

    private final int queueDepth;
    private final ConcurrentHashMap<String, ThreadPoolExecutor> stripes = new ConcurrentHashMap<>();

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong active = new AtomicLong();

    public StripedDialecticExecutor(int queueDepth) {
        if (queueDepth < 1) throw new IllegalArgumentException("queueDepth must be >= 1");
        this.queueDepth = queueDepth;
    }

    /**
     * Submit work for the given {@code (tenantId, userKey)} pair. Same-key
     * submissions are processed in submission order. The work is wrapped
     * with {@link TenantContextPropagator} so the current tenant context
     * is restored inside the worker.
     */
    public void submit(String tenantId, String userKey, Runnable work) {
        String stripeKey = tenantId + ":" + userKey;
        ThreadPoolExecutor stripe = stripes.computeIfAbsent(stripeKey, k -> newStripe());
        Runnable wrapped = TenantContextPropagator.wrap(() -> {
            active.incrementAndGet();
            try {
                work.run();
            } catch (RuntimeException e) {
                log.warn("Dialectic stripe {} threw: {}", stripeKey, e.getMessage());
            } finally {
                active.decrementAndGet();
            }
        });
        synchronized (stripe) {
            BlockingQueue<Runnable> q = stripe.getQueue();
            if (q.remainingCapacity() == 0) {
                Runnable evicted = q.poll();
                if (evicted != null) {
                    dropped.incrementAndGet();
                    log.debug("Dialectic stripe {} dropped oldest queued task (capacity={})",
                            stripeKey, queueDepth);
                }
            }
            stripe.execute(wrapped);
            submitted.incrementAndGet();
        }
    }

    public Stats stats() {
        int totalQueued = 0;
        for (ThreadPoolExecutor e : stripes.values()) {
            totalQueued += e.getQueue().size();
        }
        return new Stats(submitted.get(), dropped.get(), active.get(), totalQueued, stripes.size());
    }

    /** For tests / shutdown — block until all queued tasks finish. */
    public void shutdownAndAwait(long timeoutMillis) throws InterruptedException {
        for (ThreadPoolExecutor stripe : stripes.values()) {
            stripe.shutdown();
        }
        for (ThreadPoolExecutor stripe : stripes.values()) {
            if (!stripe.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                stripe.shutdownNow();
            }
        }
    }

    private ThreadPoolExecutor newStripe() {
        BlockingQueue<Runnable> q = new ArrayBlockingQueue<>(queueDepth);
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, q,
                Thread.ofVirtual().factory()::newThread);
    }

    public record Stats(long submitted, long dropped, long active, int queuedTotal, int stripes) {}
}
