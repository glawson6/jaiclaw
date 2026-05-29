package io.jaiclaw.gateway.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Verifies webhook request authentication based on the route's auth type.
 */
public final class WebhookAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(WebhookAuthenticator.class);
    private static final String HMAC_HEADER = "x-hub-signature-256";
    private static final String AUTH_HEADER = "authorization";

    private WebhookAuthenticator() {}

    /**
     * Verify authentication for a webhook request.
     *
     * @return true if authentication passes
     */
    public static boolean verify(WebhookRoute route, String body, Map<String, String> headers) {
        return switch (route.authType()) {
            case NONE -> true;
            case HMAC_SHA256 -> verifyHmac(route.secret(), body, headers);
            case BEARER_TOKEN -> verifyBearerToken(route.secret(), headers);
        };
    }

    private static boolean verifyHmac(String secret, String body, Map<String, String> headers) {
        String signature = findHeader(headers, HMAC_HEADER);
        if (signature == null || secret == null) {
            log.warn("HMAC verification failed: missing signature header or secret");
            return false;
        }

        try {
            String expected = "sha256=" + hmacSha256(secret, body);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.warn("HMAC verification failed: {}", e.getMessage());
            return false;
        }
    }

    private static boolean verifyBearerToken(String secret, Map<String, String> headers) {
        String authHeader = findHeader(headers, AUTH_HEADER);
        if (authHeader == null || secret == null) {
            log.warn("Bearer token verification failed: missing Authorization header or secret");
            return false;
        }

        String token = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7).trim()
                : authHeader.trim();

        return MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String hmacSha256(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static String findHeader(Map<String, String> headers, String name) {
        // Case-insensitive header lookup
        String value = headers.get(name);
        if (value != null) return value;
        for (var entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
