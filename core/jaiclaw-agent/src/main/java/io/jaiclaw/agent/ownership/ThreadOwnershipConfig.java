package io.jaiclaw.agent.ownership;

import java.time.Duration;

/**
 * Configuration for thread ownership tracking.
 *
 * @param enabled    whether thread ownership tracking is active
 * @param ttl        how long an agent retains ownership of a thread without activity
 */
public record ThreadOwnershipConfig(
        boolean enabled,
        Duration ttl
) {
    public static final Duration DEFAULT_TTL = Duration.ofHours(1);

    public ThreadOwnershipConfig {
        if (ttl == null) ttl = DEFAULT_TTL;
    }

    public static ThreadOwnershipConfig disabled() {
        return new ThreadOwnershipConfig(false, DEFAULT_TTL);
    }

    public static ThreadOwnershipConfig enabled(Duration ttl) {
        return new ThreadOwnershipConfig(true, ttl);
    }
}
