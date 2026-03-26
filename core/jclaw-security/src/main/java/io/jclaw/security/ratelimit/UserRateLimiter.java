package io.jclaw.security.ratelimit;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple per-user sliding-window rate limiter for channel-level message filtering.
 *
 * <p>Tracks message counts per user within a one-minute window. When the
 * configured maximum is exceeded, subsequent messages are rejected until the
 * window slides forward.
 *
 * <p>This rate limiter operates at the channel/gateway level (on {@code ChannelMessage}
 * identifiers), complementing the HTTP-level {@link io.jclaw.security.RateLimitFilter}
 * which operates on servlet requests.
 *
 * <p>Thread-safe — uses ConcurrentHashMap with atomic counters.
 */
public class UserRateLimiter {

    private final int maxPerMinute;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public UserRateLimiter(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    /**
     * Check if a user is within their rate limit.
     *
     * @param userId the user identifier to check
     * @return true if the request is allowed, false if rate-limited
     */
    public boolean isAllowed(String userId) {
        var counter = counters.computeIfAbsent(userId, k -> new WindowCounter());
        return counter.tryIncrement(maxPerMinute);
    }

    /**
     * Returns the number of requests remaining in the current window for a user.
     */
    public int remaining(String userId) {
        var counter = counters.get(userId);
        if (counter == null) return maxPerMinute;
        return Math.max(0, maxPerMinute - counter.currentCount());
    }

    private static class WindowCounter {
        private volatile long windowStart = Instant.now().getEpochSecond() / 60;
        private final AtomicInteger count = new AtomicInteger(0);

        boolean tryIncrement(int max) {
            long currentMinute = Instant.now().getEpochSecond() / 60;
            if (currentMinute != windowStart) {
                synchronized (this) {
                    if (currentMinute != windowStart) {
                        windowStart = currentMinute;
                        count.set(0);
                    }
                }
            }
            return count.incrementAndGet() <= max;
        }

        int currentCount() {
            long currentMinute = Instant.now().getEpochSecond() / 60;
            if (currentMinute != windowStart) return 0;
            return count.get();
        }
    }
}
