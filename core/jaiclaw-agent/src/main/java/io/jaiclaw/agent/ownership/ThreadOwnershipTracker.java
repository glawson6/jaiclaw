package io.jaiclaw.agent.ownership;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks which agent owns each thread/conversation.
 * Thread ownership expires after a configurable TTL.
 * An agent can claim a thread only if it is unowned or the current ownership has expired.
 * Mentions via {@link MentionDetector} can override existing ownership.
 */
public class ThreadOwnershipTracker {

    private static final Logger log = LoggerFactory.getLogger(ThreadOwnershipTracker.class);

    private final ConcurrentMap<String, OwnershipEntry> ownership = new ConcurrentHashMap<>();
    private final Duration defaultTtl;

    public ThreadOwnershipTracker() {
        this(Duration.ofHours(1));
    }

    public ThreadOwnershipTracker(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    /**
     * Attempt to claim a thread for an agent. Succeeds if the thread is unowned
     * or the existing ownership has expired.
     *
     * @return true if the agent now owns the thread
     */
    public boolean claim(String threadKey, String agentId) {
        return claim(threadKey, agentId, defaultTtl);
    }

    /**
     * Attempt to claim a thread for an agent with a specific TTL.
     *
     * @return true if the agent now owns the thread
     */
    public boolean claim(String threadKey, String agentId, Duration ttl) {
        OwnershipEntry current = ownership.get(threadKey);
        if (current != null && !current.isExpired() && !current.agentId().equals(agentId)) {
            log.debug("Thread {} already owned by {}, claim by {} denied",
                    threadKey, current.agentId(), agentId);
            return false;
        }

        Instant expiresAt = ttl != null ? Instant.now().plus(ttl) : null;
        ownership.put(threadKey, new OwnershipEntry(agentId, threadKey, Instant.now(), expiresAt));
        log.debug("Agent {} claimed thread {}", agentId, threadKey);
        return true;
    }

    /**
     * Force ownership transfer — used when a mention explicitly targets an agent.
     */
    public void forceAssign(String threadKey, String agentId) {
        forceAssign(threadKey, agentId, defaultTtl);
    }

    /**
     * Force ownership transfer with a specific TTL.
     */
    public void forceAssign(String threadKey, String agentId, Duration ttl) {
        Instant expiresAt = ttl != null ? Instant.now().plus(ttl) : null;
        ownership.put(threadKey, new OwnershipEntry(agentId, threadKey, Instant.now(), expiresAt));
        log.debug("Agent {} force-assigned to thread {}", agentId, threadKey);
    }

    /**
     * Get the current owner of a thread, or empty if unowned/expired.
     */
    public Optional<String> getOwner(String threadKey) {
        OwnershipEntry entry = ownership.get(threadKey);
        if (entry == null || entry.isExpired()) {
            if (entry != null && entry.isExpired()) {
                ownership.remove(threadKey);
            }
            return Optional.empty();
        }
        return Optional.of(entry.agentId());
    }

    /**
     * Release ownership of a thread.
     */
    public void release(String threadKey) {
        ownership.remove(threadKey);
    }

    /**
     * Remove all expired entries.
     */
    public void evictExpired() {
        ownership.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /**
     * Number of active (non-expired) ownership entries.
     */
    public int size() {
        evictExpired();
        return ownership.size();
    }
}
