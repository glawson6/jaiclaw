package io.jaiclaw.identity.provider;

import java.time.Instant;
import java.util.List;

/**
 * Something happened to a user or organization at the identity provider.
 *
 * <p>Deliberately a small, closed vocabulary rather than a passthrough of any
 * one provider's event catalogue. Providers publish dozens of events; only
 * these change what JaiClaw is permitted to do, and translating into a narrow
 * set keeps consumers from having to know whose webhook fired.
 *
 * @param type           what happened
 * @param subject        the affected user's provider subject, when user-scoped
 * @param organizationId the affected organization, when organization-scoped
 * @param roles          roles after the change, for membership updates
 * @param occurredAt     provider-reported timestamp
 *
 * <p>1.4.0.
 */
public record IdentityLifecycleEvent(
        Type type,
        String subject,
        String organizationId,
        List<String> roles,
        Instant occurredAt
) {
    public enum Type {
        /** A user's organization membership or roles changed. Cached authority is stale. */
        MEMBERSHIP_CHANGED,
        /** A user was suspended or reactivated. Check {@code suspended} via the client. */
        USER_SUSPENSION_CHANGED,
        /** A user was deleted. Downstream data should be erased. */
        USER_DELETED,
        /** An organization was created. */
        ORGANIZATION_CREATED,
        /** An organization was deleted. Its tenant data should be retired. */
        ORGANIZATION_DELETED,
        /**
         * Recognised as well-formed but not actionable here. Kept rather than
         * discarded so a handler can log what it chose to ignore.
         */
        OTHER
    }

    public IdentityLifecycleEvent {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
