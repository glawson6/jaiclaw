package io.jaiclaw.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ApiKeyStore} backed by {@code jaiclaw.security.api-keys[]}.
 *
 * <h2>Why keys are indexed by hash</h2>
 * A naive multi-key store loops over its entries calling
 * {@code MessageDigest.isEqual} and returns on the first match. That leaks
 * information: the number of comparisons performed — and so the response time —
 * varies with how many entries were examined before the match, letting an
 * attacker learn something about key ordering and prefixes.
 *
 * <p>Instead every configured key is hashed once at construction into a map
 * keyed by {@code sha256(key)}. Lookup hashes the presented key and performs a
 * single map probe, then one {@link MessageDigest#isEqual} on the stored hash
 * to confirm. Work is therefore independent of both the number of entries and
 * of which entry matched.
 *
 * <p>Hashing here is an <em>index</em>, not password storage: the keys are
 * high-entropy secrets supplied by the operator, so a plain SHA-256 is the
 * right primitive and a slow KDF would only add latency to every request.
 *
 * <p>This is a new convention in the codebase — no prior hashed-credential
 * lookup existed. The constant-time comparison mirrors
 * {@code io.jaiclaw.channel.util.WebhookSignatureUtil#constantTimeEquals},
 * reimplemented rather than imported because {@code jaiclaw-security} depends
 * only on {@code jaiclaw-core}.
 *
 * <p>1.2.0.
 */
public class ConfigApiKeyStore implements ApiKeyStore {

    private static final Logger log = LoggerFactory.getLogger(ConfigApiKeyStore.class);

    /** sha256(key) hex -> identity. Immutable after construction. */
    private final Map<String, Entry> byKeyHash;

    /**
     * @param resolvedKeys configured entries whose key material has already been
     *                     resolved to a literal value (env placeholders expanded,
     *                     secret refs dereferenced)
     */
    public ConfigApiKeyStore(List<ResolvedApiKey> resolvedKeys) {
        Map<String, Entry> index = new LinkedHashMap<>();
        if (resolvedKeys != null) {
            for (ResolvedApiKey resolved : resolvedKeys) {
                if (resolved == null || resolved.key() == null || resolved.key().isBlank()) {
                    continue;
                }
                String hash = sha256Hex(resolved.key());
                Entry previous = index.put(hash, new Entry(hash, resolved.identity()));
                if (previous != null) {
                    // Two entries sharing key material is always a mistake — the later
                    // one silently shadowed the earlier, which would hand a caller
                    // authority they were not meant to have.
                    throw new IllegalStateException(
                            "Duplicate API key material: '" + previous.identity().keyName()
                                    + "' and '" + resolved.identity().keyName()
                                    + "' resolve to the same key. Each entry must have distinct "
                                    + "key material; duplicate (tenant, role) pairs are fine, "
                                    + "duplicate keys are not.");
                }
            }
        }
        this.byKeyHash = Map.copyOf(index);
        log.debug("ConfigApiKeyStore initialized with {} key(s)", this.byKeyHash.size());
    }

    @Override
    public Optional<ApiKeyIdentity> findByKey(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return Optional.empty();
        }
        String presentedHash = sha256Hex(presentedKey);
        Entry entry = byKeyHash.get(presentedHash);
        if (entry == null) {
            return Optional.empty();
        }
        // The map probe already matched, but confirm in constant time so the
        // comparison itself never becomes the distinguishing signal.
        if (!constantTimeEquals(presentedHash, entry.keyHash())) {
            return Optional.empty();
        }
        return Optional.of(entry.identity());
    }

    @Override
    public int size() {
        return byKeyHash.size();
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; absence is not a recoverable condition.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private record Entry(String keyHash, ApiKeyIdentity identity) {}

    /**
     * A configured entry whose key material has been resolved to a literal value.
     *
     * @param key      the resolved key material
     * @param identity the tenant and role it is bound to
     */
    public record ResolvedApiKey(String key, ApiKeyIdentity identity) {}
}
