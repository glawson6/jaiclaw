package io.jaiclaw.subscription;

import java.time.Instant;
import java.util.Map;

/**
 * A user's subscription to a plan.
 *
 * @param id              unique subscription identifier
 * @param userId          the subscribing user
 * @param planId          the subscription plan
 * @param status          current status
 * @param startedAt       when the subscription started
 * @param expiresAt       when the subscription expires
 * @param paymentProvider which payment provider was used
 * @param externalId      external subscription/checkout ID from the payment provider
 * @param metadata        additional key-value metadata
 */
public record Subscription(
        String id,
        String userId,
        String planId,
        SubscriptionStatus status,
        Instant startedAt,
        Instant expiresAt,
        String paymentProvider,
        String externalId,
        Map<String, String> metadata
) {
    public Subscription {
        if (metadata == null) metadata = Map.of();
    }

    public Subscription withStatus(SubscriptionStatus newStatus) {
        return new Subscription(id, userId, planId, newStatus, startedAt, expiresAt,
                paymentProvider, externalId, metadata);
    }

    public Subscription withExternalId(String newExternalId) {
        return new Subscription(id, userId, planId, status, startedAt, expiresAt,
                paymentProvider, newExternalId, metadata);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }
}
