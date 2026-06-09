package io.jaiclaw.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Validates inbound messages on external transports (Kafka, AMQP, HTTP headers).
 *
 * <p>Follows the same verification pattern as {@code WebhookAuthenticator} in
 * {@code io.jaiclaw.gateway.webhook}, intentionally duplicated to avoid an
 * upward dependency on the gateway module.
 */
public class PipelineTransportAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(PipelineTransportAuthenticator.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HMAC_PREFIX = "sha256=";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Verify an inbound message against the configured transport authentication.
     * Returns silently on success; throws {@link PipelineSecurityException} on failure.
     *
     * @param auth      the transport auth config (null = pass-through)
     * @param body      the message body
     * @param headers   the message headers (case-insensitive lookup)
     * @param pipelineId the pipeline ID (for error context)
     * @param stageName  the stage name (for error context)
     */
    public void verify(StageDefinition.TransportAuth auth, String body,
                        Map<String, String> headers, String pipelineId, String stageName) {
        if (auth == null || auth.authType() == TransportAuthType.NONE) {
            return;
        }

        switch (auth.authType()) {
            case HMAC_SHA256 -> verifyHmac(auth.secret(), body, headers, auth.headerName(), pipelineId, stageName);
            case BEARER_TOKEN -> verifyBearerToken(auth.secret(), headers, auth.headerName(), pipelineId, stageName);
            default -> { /* NONE already handled */ }
        }
    }

    private void verifyHmac(String secret, String body, Map<String, String> headers,
                             String headerName, String pipelineId, String stageName) {
        String signature = findHeader(headers, headerName);
        if (signature == null || signature.isBlank()) {
            throw new PipelineSecurityException(pipelineId, stageName,
                    "Missing HMAC signature header: " + headerName);
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] computed = mac.doFinal(body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            String expected = HMAC_PREFIX + HexFormat.of().formatHex(computed);

            // Timing-safe comparison
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new PipelineSecurityException(pipelineId, stageName,
                        "HMAC signature verification failed");
            }
        } catch (PipelineSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new PipelineSecurityException(pipelineId, stageName,
                    "HMAC verification error: " + e.getMessage());
        }
    }

    private void verifyBearerToken(String secret, Map<String, String> headers,
                                    String headerName, String pipelineId, String stageName) {
        String token = findHeader(headers, headerName);
        if (token == null || token.isBlank()) {
            throw new PipelineSecurityException(pipelineId, stageName,
                    "Missing bearer token header: " + headerName);
        }

        // Strip "Bearer " prefix if present
        if (token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }

        // Timing-safe comparison
        if (!MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new PipelineSecurityException(pipelineId, stageName,
                    "Bearer token verification failed");
        }
    }

    /**
     * Case-insensitive header lookup.
     */
    static String findHeader(Map<String, String> headers, String name) {
        if (headers == null || name == null) return null;

        // Try exact match first
        String value = headers.get(name);
        if (value != null) return value;

        // Case-insensitive fallback
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
