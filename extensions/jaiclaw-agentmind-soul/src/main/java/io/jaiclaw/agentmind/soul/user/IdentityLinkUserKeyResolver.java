package io.jaiclaw.agentmind.soul.user;

import io.jaiclaw.identity.IdentityResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Default {@link AgentMindUserKeyResolver}. Delegates to
 * {@link IdentityResolver} when {@code jaiclaw-identity} is present, falling
 * back to a deterministic SHA-256-derived hash when the resolver is null.
 *
 * <p>When the linked path is taken, the returned key is the
 * {@code IdentityResolver.resolve(channel, peerId)} string (typically a
 * canonical UUID). When the fallback is taken, the key is a 16-character
 * lowercase hex prefix of {@code sha256(channel:peerId)} — short enough to
 * fit in filenames / Redis keys, deterministic enough that same-channel
 * sessions continue across restarts.
 *
 * <p>Cross-channel continuity requires the linked path.
 */
public class IdentityLinkUserKeyResolver implements AgentMindUserKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(IdentityLinkUserKeyResolver.class);

    private final IdentityResolver identityResolver;
    private final boolean warnedAboutFallback;

    public IdentityLinkUserKeyResolver(IdentityResolver identityResolver) {
        this.identityResolver = identityResolver;
        this.warnedAboutFallback = false;
        if (identityResolver == null) {
            log.warn("AgentMindUserKeyResolver: jaiclaw-identity not on classpath — "
                    + "per-user state degrades to per-channel deterministic-hash keying. "
                    + "Cross-channel continuity is disabled. Add jaiclaw-identity to "
                    + "your classpath to enable canonical user keys.");
        }
    }

    @Override
    public String resolve(String channel, String peerId) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(peerId, "peerId");
        if (identityResolver != null) {
            return identityResolver.resolve(channel, peerId);
        }
        return deterministicHash(channel, peerId);
    }

    @Override
    public boolean isLinkedKeyAvailable() {
        return identityResolver != null;
    }

    static String deterministicHash(String channel, String peerId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((channel + ":" + peerId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required to be available on every JVM (JLS).
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
