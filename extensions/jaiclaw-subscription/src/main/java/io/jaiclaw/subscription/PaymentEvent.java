package io.jaiclaw.subscription;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * An event from a payment provider (webhook callback or verification result).
 *
 * @param id             unique event identifier
 * @param subscriptionId associated subscription
 * @param provider       payment provider name
 * @param type           event type
 * @param amount         payment amount (if applicable)
 * @param currency       ISO 4217 currency code
 * @param timestamp      when the event occurred
 * @param raw            raw key-value data from the provider
 */
public record PaymentEvent(
        String id,
        String subscriptionId,
        String provider,
        PaymentEventType type,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        Map<String, String> raw
) {
    public PaymentEvent {
        if (raw == null) raw = Map.of();
        if (timestamp == null) timestamp = Instant.now();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String subscriptionId;
        private String provider;
        private PaymentEventType type;
        private BigDecimal amount;
        private String currency;
        private Instant timestamp;
        private Map<String, String> raw;

        public Builder id(String id) { this.id = id; return this; }
        public Builder subscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; return this; }
        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder type(PaymentEventType type) { this.type = type; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder raw(Map<String, String> raw) { this.raw = raw; return this; }

        public PaymentEvent build() {
            return new PaymentEvent(id, subscriptionId, provider, type, amount, currency, timestamp, raw);
        }
    }
}
