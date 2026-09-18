package io.jaiclaw.identity.link;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for the channel-linking ceremony ({@code jaiclaw.identity.link.*}).
 *
 * <p>Provider-neutral — these are plain OAuth authorization-code parameters.
 *
 * @param enabled       whether linking is available. Default false.
 * @param authorizeUri  the provider's authorization endpoint
 * @param tokenUri      the provider's token endpoint
 * @param clientId      OAuth client id registered for this gateway
 * @param clientSecret  client secret. Omit for a public client using PKCE alone.
 * @param redirectUri   absolute callback URL, must exactly match what is
 *                      registered with the provider
 * @param scopes        scopes requested. Defaults to {@code [openid, profile]}.
 * @param resource      optional RFC 8707 resource indicator, when the provider
 *                      requires one to mint a correctly-audienced token
 * @param nonceTtl      how long a pending request stays redeemable. Default 10
 *                      minutes — long enough for a person to authenticate,
 *                      short enough to bound the replay window.
 *
 * <p>1.4.0.
 */
@ConfigurationProperties(prefix = "jaiclaw.identity.link")
public record ChannelLinkProperties(
        boolean enabled,
        String authorizeUri,
        String tokenUri,
        String clientId,
        String clientSecret,
        String redirectUri,
        List<String> scopes,
        String resource,
        Duration nonceTtl
) {
    public static final List<String> DEFAULT_SCOPES = List.of("openid", "profile");
    public static final Duration DEFAULT_NONCE_TTL = Duration.ofMinutes(10);

    @ConstructorBinding
    public ChannelLinkProperties {
        scopes = (scopes == null || scopes.isEmpty()) ? DEFAULT_SCOPES : List.copyOf(scopes);
        if (nonceTtl == null || nonceTtl.isZero() || nonceTtl.isNegative()) {
            nonceTtl = DEFAULT_NONCE_TTL;
        }
        if (clientSecret != null && clientSecret.isBlank()) clientSecret = null;
        if (resource != null && resource.isBlank()) resource = null;
    }

    /**
     * Programmatic defaults.
     *
     * <p>A {@code public static} factory rather than a no-arg constructor —
     * Spring Boot 4's record binder picks a public constructor by parameter
     * count and can silently choose an overload over the canonical one, which
     * drops nested YAML values.
     */
    public static ChannelLinkProperties defaults() {
        return new ChannelLinkProperties(false, null, null, null, null, null, null, null, null);
    }

    /** Whether enough is configured to run the flow. */
    public boolean isUsable() {
        return enabled
                && authorizeUri != null && !authorizeUri.isBlank()
                && tokenUri != null && !tokenUri.isBlank()
                && clientId != null && !clientId.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }
}
