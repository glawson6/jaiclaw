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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String checkoutUrl;
        private String sessionId;
        private String provider;
        private Map<String, String> metadata;

        public Builder checkoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }

        public CheckoutResult build() {
            return new CheckoutResult(checkoutUrl, sessionId, provider, metadata);
        }
    }
}
