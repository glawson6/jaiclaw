package io.jaiclaw.subscription;

import java.util.Map;

/**
 * Result of creating a checkout session with a payment provider.
 *
 * @param checkoutUrl  URL to redirect the user to for payment
 * @param sessionId    provider-specific session/checkout ID
 * @param provider     payment provider name
 * @param metadata     additional data from the provider
 */
public record CheckoutResult(
        String checkoutUrl,
        String sessionId,
        String provider,
        Map<String, String> metadata
) {
    public CheckoutResult {
        if (metadata == null) metadata = Map.of();
    }
}
