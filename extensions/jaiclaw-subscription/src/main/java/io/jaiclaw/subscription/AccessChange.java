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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String userId;
        private String groupId;
        private AccessChangeType type;
        private Instant effectiveAt;
        private String reason;

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder groupId(String groupId) { this.groupId = groupId; return this; }
        public Builder type(AccessChangeType type) { this.type = type; return this; }
        public Builder effectiveAt(Instant effectiveAt) { this.effectiveAt = effectiveAt; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }

        public AccessChange build() {
            return new AccessChange(userId, groupId, type, effectiveAt, reason);
        }
    }
}
