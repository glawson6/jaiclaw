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
        Map<String, String> metadata,
        String tenantId
) {
    public Subscription {
        if (metadata == null) metadata = Map.of();
    }

    /** Backward-compatible constructor without tenantId. */
    public Subscription(String id, String userId, String planId, SubscriptionStatus status,
                        Instant startedAt, Instant expiresAt, String paymentProvider,
                        String externalId, Map<String, String> metadata) {
        this(id, userId, planId, status, startedAt, expiresAt, paymentProvider, externalId, metadata, null);
    }

    public Subscription withStatus(SubscriptionStatus newStatus) {
        return new Subscription(id, userId, planId, newStatus, startedAt, expiresAt,
                paymentProvider, externalId, metadata, tenantId);
    }

    public Subscription withExternalId(String newExternalId) {
        return new Subscription(id, userId, planId, status, startedAt, expiresAt,
                paymentProvider, newExternalId, metadata, tenantId);
    }

    public Subscription withTenantId(String newTenantId) {
        return new Subscription(id, userId, planId, status, startedAt, expiresAt,
                paymentProvider, externalId, metadata, newTenantId);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String userId;
        private String planId;
        private SubscriptionStatus status;
        private Instant startedAt;
        private Instant expiresAt;
        private String paymentProvider;
        private String externalId;
        private Map<String, String> metadata;
        private String tenantId;

        public Builder id(String id) { this.id = id; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder planId(String planId) { this.planId = planId; return this; }
        public Builder status(SubscriptionStatus status) { this.status = status; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder paymentProvider(String paymentProvider) { this.paymentProvider = paymentProvider; return this; }
        public Builder externalId(String externalId) { this.externalId = externalId; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }

        public Subscription build() {
            return new Subscription(
                    id, userId, planId, status, startedAt, expiresAt, paymentProvider, externalId, metadata, tenantId);
        }
    }
}
