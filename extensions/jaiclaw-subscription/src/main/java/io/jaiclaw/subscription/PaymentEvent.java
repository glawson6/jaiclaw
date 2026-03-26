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
}
