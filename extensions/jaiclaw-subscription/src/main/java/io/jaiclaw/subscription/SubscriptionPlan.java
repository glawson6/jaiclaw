package io.jaiclaw.subscription;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * A subscription plan defining pricing and duration.
 *
 * @param id          unique plan identifier (e.g. "monthly", "yearly")
 * @param name        display name
 * @param description optional description
 * @param duration    how long the subscription lasts
 * @param price       price amount
 * @param currency    ISO 4217 currency code (e.g. "USD")
 * @param metadata    additional key-value metadata
 */
public record SubscriptionPlan(
        String id,
        String name,
        String description,
        Duration duration,
        BigDecimal price,
        String currency,
        Map<String, String> metadata
) {
    public SubscriptionPlan {
        if (metadata == null) metadata = Map.of();
    }
}
