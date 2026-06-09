package io.jaiclaw.pipeline;

/**
 * Authentication method for inter-stage transport verification.
 *
 * <p>Intentionally separate from {@code io.jaiclaw.gateway.webhook.WebhookAuthType}
 * to avoid adding a gateway dependency to the pipeline module.
 */
public enum TransportAuthType {
    /** No authentication — pass-through. */
    NONE,
    /** HMAC-SHA256 signature verification (sha256= prefix, hex-encoded). */
    HMAC_SHA256,
    /** Bearer token comparison. */
    BEARER_TOKEN
}
