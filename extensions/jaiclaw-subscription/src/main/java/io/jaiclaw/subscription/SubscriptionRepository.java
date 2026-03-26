package io.jaiclaw.subscription;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SPI for subscription persistence.
 */
public interface SubscriptionRepository {

    Optional<Subscription> findById(String id);

    List<Subscription> findByUserId(String userId);

    List<Subscription> findExpired(Instant before);

    Subscription save(Subscription subscription);

    void deleteById(String id);
}
