package io.jclaw.channel;

import java.util.Map;

/**
 * Result of attempting to deliver an outbound message via a channel adapter.
 */
public sealed interface DeliveryResult {

    record Success(
            String platformMessageId,
            Map<String, Object> platformData
    ) implements DeliveryResult {
        public Success(String platformMessageId) {
            this(platformMessageId, Map.of());
        }
    }

    record Failure(
            String errorCode,
            String message,
            boolean retryable
    ) implements DeliveryResult {}
}
