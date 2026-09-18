package io.jaiclaw.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the stable per-user key that per-user state (AgentMind memory,
 * tendencies) is stored under.
 *
 * <h2>The migration problem</h2>
 * Per-user state has always been keyed by {@code sha256(channelId:peerId)}
 * truncated to 16 hex characters. That key is per-channel and derived from an
 * unverified platform id, so the same person on Slack and Telegram is two
 * different users, and nothing ties either to a real identity.
 *
 * <p>Switching to a verified canonical subject is the right destination, but a
 * naive switch <strong>changes every key and orphans all stored state</strong> —
 * silently, with the symptom being users who appear to have been forgotten.
 *
 * <p>So this resolver <strong>dual-reads</strong>:
 * <ul>
 *   <li>{@link #resolveForRead} returns the canonical key first and the legacy
 *       hash second, so a caller can try each in turn and find pre-migration
 *       state that was never rewritten.</li>
 *   <li>{@link #resolveForWrite} returns the canonical key when a
 *       <em>verified</em> link exists, and the legacy hash otherwise — so state
 *       migrates as users link, and unlinked users are unaffected.</li>
 * </ul>
 *
 * <p>Only <em>verified</em> links are honoured. An asserted link proves nothing,
 * and keying authorization-relevant state on an unproven claim would let anyone
 * who can call {@code link()} adopt another user's memory.
 *
 * <p>Gate with {@code jaiclaw.agentmind.canonical-user-keys=true}; default off,
 * so the migration is opt-in and reversible.
 *
 * <p>1.4.0.
 */
public class CanonicalUserKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(CanonicalUserKeyResolver.class);

    private final IdentityLinkStore linkStore;
    private final boolean enabled;

    public CanonicalUserKeyResolver(IdentityLinkStore linkStore, boolean enabled) {
        this.linkStore = linkStore;
        this.enabled = enabled;
        if (enabled && linkStore == null) {
            log.warn("jaiclaw.agentmind.canonical-user-keys=true but no IdentityLinkStore is "
                    + "available — per-user keys stay on the legacy hash.");
        }
    }

    /**
     * Keys to try when reading, most-preferred first.
     *
     * <p>Callers should try each in order and use the first that yields state.
     * The list always ends with the legacy hash, so state written before the
     * migration remains reachable indefinitely.
     */
    public List<String> resolveForRead(String channelId, String peerId) {
        String legacy = legacyHash(channelId, peerId);
        if (legacy == null) {
            return List.of();
        }
        return canonicalKey(channelId, peerId)
                .map(canonical -> List.of(canonical, legacy))
                .orElseGet(() -> List.of(legacy));
    }

    /**
     * The key to write under.
     *
     * <p>The canonical subject once the channel user has a verified link;
     * the legacy hash until then. State therefore migrates on the first write
     * after linking, without a batch job and without a window where reads fail.
     */
    public String resolveForWrite(String channelId, String peerId) {
        return canonicalKey(channelId, peerId)
                .orElseGet(() -> legacyHash(channelId, peerId));
    }

    /** Whether this pair currently resolves to a verified canonical key. */
    public boolean hasCanonicalKey(String channelId, String peerId) {
        return canonicalKey(channelId, peerId).isPresent();
    }

    private Optional<String> canonicalKey(String channelId, String peerId) {
        if (!enabled || linkStore == null || channelId == null || peerId == null) {
            return Optional.empty();
        }
        return linkStore.findLink(channelId, peerId)
                // Asserted links are not good enough: anyone able to call link()
                // could otherwise adopt another user's stored state.
                .filter(link -> link.isVerified())
                .map(link -> link.canonicalUserId());
    }

    /**
     * The pre-1.4.0 key: a 16-character hex prefix of
     * {@code sha256(channelId:peerId)}.
     *
     * <p>Must stay byte-identical to the original implementation or every
     * existing key is orphaned.
     */
    public static String legacyHash(String channelId, String peerId) {
        if (channelId == null || channelId.isBlank()) return null;
        if (peerId == null || peerId.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((channelId + ":" + peerId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
