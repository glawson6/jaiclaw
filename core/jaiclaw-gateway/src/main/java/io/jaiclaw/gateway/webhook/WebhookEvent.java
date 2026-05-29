package io.jaiclaw.gateway.webhook;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an incoming webhook event.
 *
 * @param path      the matched route path
 * @param headers   HTTP headers from the request
 * @param body      the raw request body
 * @param timestamp when the event was received
 */
public record WebhookEvent(
        String path,
        Map<String, String> headers,
        String body,
        Instant timestamp
) {
    public WebhookEvent {
        if (headers == null) headers = Map.of();
        if (timestamp == null) timestamp = Instant.now();
    }
}
