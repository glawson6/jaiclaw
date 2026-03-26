package io.jaiclaw.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Central service for subscription management. Handles plan lookup, subscription CRUD,
 * checkout delegation, and lifecycle event firing.
 */
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository repository;
    private final Map<String, PaymentProvider> paymentProviders;
    private final List<SubscriptionLifecycleListener> listeners;
    private final Map<String, SubscriptionPlan> plans;

    public SubscriptionService(SubscriptionRepository repository,
                               List<PaymentProvider> paymentProviders,
                               List<SubscriptionLifecycleListener> listeners,
                               List<SubscriptionPlan> plans) {
        this.repository = repository;
        this.paymentProviders = new LinkedHashMap<>();
        for (var p : paymentProviders) {
            this.paymentProviders.put(p.name(), p);
        }
        this.listeners = listeners != null ? listeners : List.of();
        this.plans = new LinkedHashMap<>();
        for (var plan : plans) {
            this.plans.put(plan.id(), plan);
        }
    }

    public List<SubscriptionPlan> listPlans() {
        return List.copyOf(plans.values());
    }

    public Optional<SubscriptionPlan> getPlan(String planId) {
        return Optional.ofNullable(plans.get(planId));
    }

    public Optional<Subscription> getSubscription(String id) {
        return repository.findById(id);
    }

    public List<Subscription> getUserSubscriptions(String userId) {
        return repository.findByUserId(userId);
    }

    public Optional<Subscription> getActiveSubscription(String userId) {
        return repository.findByUserId(userId).stream()
                .filter(Subscription::isActive)
                .findFirst();
    }

    /**
     * Initiate a checkout for the given user and plan via the specified payment provider.
     */
    public CheckoutResult createCheckout(String userId, String planId, String providerName,
                                          Map<String, String> metadata) {
        var plan = plans.get(planId);
        if (plan == null) throw new IllegalArgumentException("Unknown plan: " + planId);

        var provider = paymentProviders.get(providerName);
        if (provider == null) throw new IllegalArgumentException("Unknown payment provider: " + providerName);

        // Create subscription in pending state
        var subscription = new Subscription(
                generateId(),
                userId,
                planId,
                SubscriptionStatus.ACTIVE, // Will be activated upon payment confirmation
                null, null,
                providerName,
                null,
                metadata != null ? metadata : Map.of()
        );

        var checkoutMeta = new LinkedHashMap<>(metadata != null ? metadata : Map.of());
        checkoutMeta.put("subscription_id", subscription.id());
        checkoutMeta.put("user_id", userId);

        var result = provider.createCheckout(userId, plan, checkoutMeta);

        // Save with external ID from checkout
        repository.save(subscription.withExternalId(result.sessionId()));

        log.info("Checkout created for user={}, plan={}, provider={}, subscriptionId={}",
                userId, planId, providerName, subscription.id());

        return result;
    }

    /**
     * Activate a subscription after successful payment.
     */
    public Subscription activate(String subscriptionId, String groupId) {
        var sub = repository.findById(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("Cannot activate unknown subscription: {}", subscriptionId);
            return null;
        }

        var plan = plans.get(sub.planId());
        Instant now = Instant.now();
        Instant expiresAt = plan != null ? now.plus(plan.duration()) : now;

        var activated = new Subscription(
                sub.id(), sub.userId(), sub.planId(),
                SubscriptionStatus.ACTIVE,
                now, expiresAt,
                sub.paymentProvider(), sub.externalId(), sub.metadata()
        );
        repository.save(activated);

        var change = new AccessChange(sub.userId(), groupId, AccessChangeType.GRANT,
                now, "Subscription activated: " + sub.planId());
        fireActivated(change);

        log.info("Subscription activated: id={}, user={}, plan={}, expires={}",
                sub.id(), sub.userId(), sub.planId(), expiresAt);

        return activated;
    }

    /**
     * Cancel a subscription.
     */
    public Subscription cancel(String subscriptionId, String groupId) {
        var sub = repository.findById(subscriptionId).orElse(null);
        if (sub == null) return null;

        // Cancel with payment provider if external ID exists
        if (sub.externalId() != null && sub.paymentProvider() != null) {
            var provider = paymentProviders.get(sub.paymentProvider());
            if (provider != null) {
                try {
                    provider.cancelSubscription(sub.externalId());
                } catch (Exception e) {
                    log.warn("Failed to cancel with provider {}: {}", sub.paymentProvider(), e.getMessage());
                }
            }
        }

        var cancelled = sub.withStatus(SubscriptionStatus.CANCELLED);
        repository.save(cancelled);

        var change = new AccessChange(sub.userId(), groupId, AccessChangeType.REVOKE,
                Instant.now(), "Subscription cancelled");
        fireCancelled(change);

        log.info("Subscription cancelled: id={}, user={}", sub.id(), sub.userId());
        return cancelled;
    }

    /**
     * Process a webhook payload from a payment provider.
     */
    public Optional<PaymentEvent> handleWebhook(String providerName, String payload,
                                                 Map<String, String> headers) {
        var provider = paymentProviders.get(providerName);
        if (provider == null) {
            log.warn("Unknown payment provider for webhook: {}", providerName);
            return Optional.empty();
        }

        return provider.handleWebhook(payload, headers);
    }

    /**
     * Process expired subscriptions. Called by the scheduler.
     */
    public List<Subscription> processExpired(String groupId) {
        var expired = repository.findExpired(Instant.now());
        List<Subscription> processed = new ArrayList<>();

        for (var sub : expired) {
            if (sub.status() == SubscriptionStatus.ACTIVE || sub.status() == SubscriptionStatus.PAST_DUE) {
                var updated = sub.withStatus(SubscriptionStatus.EXPIRED);
                repository.save(updated);

                var change = new AccessChange(sub.userId(), groupId, AccessChangeType.REVOKE,
                        Instant.now(), "Subscription expired");
                fireRevoked(change);

                log.info("Subscription expired: id={}, user={}", sub.id(), sub.userId());
                processed.add(updated);
            }
        }

        return processed;
    }

    /**
     * Mark a subscription as past due (e.g. after a payment failure).
     */
    public Subscription markPastDue(String subscriptionId, String groupId) {
        var sub = repository.findById(subscriptionId).orElse(null);
        if (sub == null) return null;

        var updated = sub.withStatus(SubscriptionStatus.PAST_DUE);
        repository.save(updated);

        var change = new AccessChange(sub.userId(), groupId, AccessChangeType.REVOKE,
                Instant.now(), "Payment failed — subscription past due");
        firePastDue(change);

        return updated;
    }

    private void fireActivated(AccessChange change) {
        listeners.forEach(l -> {
            try { l.onActivated(change); }
            catch (Exception e) { log.error("Listener error on activated: {}", e.getMessage(), e); }
        });
    }

    private void fireRevoked(AccessChange change) {
        listeners.forEach(l -> {
            try { l.onRevoked(change); }
            catch (Exception e) { log.error("Listener error on revoked: {}", e.getMessage(), e); }
        });
    }

    private void fireCancelled(AccessChange change) {
        listeners.forEach(l -> {
            try { l.onCancelled(change); }
            catch (Exception e) { log.error("Listener error on cancelled: {}", e.getMessage(), e); }
        });
    }

    private void firePastDue(AccessChange change) {
        listeners.forEach(l -> {
            try { l.onPastDue(change); }
            catch (Exception e) { log.error("Listener error on pastDue: {}", e.getMessage(), e); }
        });
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
