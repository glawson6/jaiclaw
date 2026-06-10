package io.jaiclaw.subscription;

import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantContextPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks for expired subscriptions and transitions their status.
 * Uses a {@link ScheduledExecutorService} (not CronService — this is programmatic, not LLM-driven).
 *
 * <p><b>Tenant context.</b> The thread-local tenant context is captured at
 * {@link #start()} time and propagated to every {@link #checkExpired()} run
 * via {@link TenantContextPropagator}. Each scheduler instance belongs to one
 * tenant; in MULTI mode, callers typically construct one scheduler per tenant.
 * If no tenant context was set at start time, the scheduled tasks run with no
 * context (correct for SINGLE mode).
 */
public class SubscriptionExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryScheduler.class);

    private final SubscriptionService subscriptionService;
    private final String groupId;
    private final Duration interval;
    private ScheduledExecutorService executor;

    public SubscriptionExpiryScheduler(SubscriptionService subscriptionService,
                                       String groupId,
                                       Duration interval) {
        this.subscriptionService = subscriptionService;
        this.groupId = groupId;
        this.interval = interval;
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Thread.ofVirtual().unstarted(r);
            t.setName("subscription-expiry-scheduler");
            return t;
        });

        // Capture the calling thread's tenant context (may be null in SINGLE mode)
        // and restore it on each scheduled run via TenantContextPropagator.wrap.
        TenantContext capturedAtStart = TenantContextHolder.get();
        Runnable task = TenantContextPropagator.wrap(this::checkExpired);

        executor.scheduleAtFixedRate(task,
                interval.toSeconds(), interval.toSeconds(), TimeUnit.SECONDS);

        log.info("Subscription expiry scheduler started (interval={}, tenant={})",
                interval, capturedAtStart != null ? capturedAtStart.getTenantId() : "<none>");
    }

    public void stop() {
        if (executor != null) {
            executor.shutdown();
            log.info("Subscription expiry scheduler stopped");
        }
    }

    private void checkExpired() {
        try {
            var expired = subscriptionService.processExpired(groupId);
            if (!expired.isEmpty()) {
                log.info("Processed {} expired subscriptions", expired.size());
            }
        } catch (Exception e) {
            log.error("Error checking expired subscriptions: {}", e.getMessage(), e);
        }
    }
}
