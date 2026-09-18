package io.jaiclaw.identity.provider;

import java.util.Map;
import java.util.Optional;

/**
 * Verifies and parses an identity provider's lifecycle webhook.
 *
 * <p>Provider-specific by necessity: signature schemes differ (header name,
 * algorithm, what exactly is signed), and so do payload shapes. The interface
 * exists so consumers can handle lifecycle events without knowing which
 * provider sent them.
 *
 * <p><strong>Implementations must verify before parsing</strong>, and must
 * compute the signature over the <em>raw</em> request body. Re-serializing
 * parsed JSON changes the bytes and breaks the comparison — usually
 * intermittently, which is worse than breaking outright.
 *
 * <p>1.4.0.
 */
public interface IdentityProviderWebhookVerifier {

    /**
     * Whether the request genuinely came from the configured provider.
     *
     * @param rawBody the request body exactly as received, unparsed
     * @param headers request headers, lowercase keys
     */
    boolean verify(String rawBody, Map<String, String> headers);

    /**
     * Translate a verified payload into the neutral event vocabulary.
     *
     * <p>Only call after {@link #verify} has returned true. Returns empty when
     * the payload is well-formed but carries no event this vocabulary
     * represents.
     */
    Optional<IdentityLifecycleEvent> parse(String rawBody);

    /** Identifies this implementation in logs and diagnostics. */
    String providerName();
}
