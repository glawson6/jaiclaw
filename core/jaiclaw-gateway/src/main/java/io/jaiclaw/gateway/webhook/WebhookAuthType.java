package io.jaiclaw.gateway.webhook;

/**
 * Authentication type for webhook routes.
 */
public enum WebhookAuthType {
    /** No authentication required. */
    NONE,
    /** HMAC-SHA256 signature verification via X-Hub-Signature-256 header. */
    HMAC_SHA256,
    /** Bearer token verification via Authorization header. */
    BEARER_TOKEN
}
