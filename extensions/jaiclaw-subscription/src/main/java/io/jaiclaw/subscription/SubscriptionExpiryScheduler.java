package io.jaiclaw.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks for expired subscriptions and transitions their status.
 * Uses a {@link ScheduledExecutorService} (not CronService — this is programmatic, not LLM-driven).
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

        executor.scheduleAtFixedRate(this::checkExpired,
                interval.toSeconds(), interval.toSeconds(), TimeUnit.SECONDS);

        log.info("Subscription expiry scheduler started (interval={})", interval);
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
