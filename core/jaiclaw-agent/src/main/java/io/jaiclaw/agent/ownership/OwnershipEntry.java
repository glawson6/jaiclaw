package io.jaiclaw.agent.ownership;

import java.time.Instant;

/**
 * Tracks which agent owns a thread/conversation.
 *
 * @param agentId   the agent that claimed the thread
 * @param threadKey unique key identifying the thread (e.g. channel:chatId:threadId)
 * @param claimedAt when ownership was claimed
 * @param expiresAt when ownership expires (null = no expiry)
 */
public record OwnershipEntry(
        String agentId,
        String threadKey,
        Instant claimedAt,
        Instant expiresAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
