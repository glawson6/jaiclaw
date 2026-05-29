package io.jaiclaw.gateway.webhook;

import java.util.function.Function;

/**
 * Defines a webhook route: path, authentication, and handler.
 *
 * @param path     the URL path suffix (e.g. "github", "stripe/events")
 * @param authType the authentication type for this route
 * @param secret   the secret for HMAC or bearer token verification (null for NONE)
 * @param handler  function that processes the event and returns a response body
 */
public record WebhookRoute(
        String path,
        WebhookAuthType authType,
        String secret,
        Function<WebhookEvent, String> handler
) {
    public WebhookRoute {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("path is required");
        if (authType == null) authType = WebhookAuthType.NONE;
        if (handler == null) throw new IllegalArgumentException("handler is required");
    }
}
