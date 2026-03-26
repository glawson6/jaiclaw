package io.jaiclaw.subscription;

import java.time.Instant;

/**
 * Represents a change in access rights due to a subscription lifecycle event.
 *
 * @param userId      the affected user
 * @param groupId     the group/channel being granted or revoked
 * @param type        GRANT or REVOKE
 * @param effectiveAt when the change takes effect
 * @param reason      human-readable reason
 */
public record AccessChange(
        String userId,
        String groupId,
        AccessChangeType type,
        Instant effectiveAt,
        String reason
) {
    public AccessChange {
        if (effectiveAt == null) effectiveAt = Instant.now();
    }
}
