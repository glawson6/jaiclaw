package io.jaiclaw.subscription;

/**
 * SPI for reacting to subscription lifecycle events.
 * Implementations handle access control changes (e.g. adding/removing users from channels).
 */
public interface SubscriptionLifecycleListener {

    void onActivated(AccessChange change);

    void onRevoked(AccessChange change);

    void onPastDue(AccessChange change);

    default void onCancelled(AccessChange change) {
        onRevoked(change);
    }
}
