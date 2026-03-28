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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String name;
        private String description;
        private Duration duration;
        private BigDecimal price;
        private String currency;
        private Map<String, String> metadata;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder duration(Duration duration) { this.duration = duration; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }

        public SubscriptionPlan build() {
            return new SubscriptionPlan(id, name, description, duration, price, currency, metadata);
        }
    }
}
