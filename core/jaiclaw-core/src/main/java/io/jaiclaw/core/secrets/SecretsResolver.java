package io.jaiclaw.core.secrets;

import io.jaiclaw.core.api.Stable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Consults a chain of {@link SecretsProvider}s in order and returns the
 * first non-{@link SecretResolution.Missing Missing} result.
 *
 * <p>Behavior on {@link SecretResolution.ProviderError ProviderError}
 * is controlled by {@link OnError}:
 * <ul>
 *   <li>{@link OnError#CONTINUE} (default) — record the error and
 *       continue to the next provider. If a later provider yields a
 *       value, the resolver returns {@link SecretResolution.Resolved Resolved};
 *       if none do, the resolver returns the first {@code ProviderError}
 *       (preserving the cause for diagnostics).</li>
 *   <li>{@link OnError#FAIL} — return the {@code ProviderError}
 *       immediately, short-circuiting the chain.</li>
 * </ul>
 *
 * <p>0.9.2 secrets baseline.
 */
@Stable
public final class SecretsResolver {

    /** Controls how the resolver handles provider-level errors. */
    public enum OnError {
        CONTINUE,
        FAIL
    }

    private final List<SecretsProvider> chain;
    private final OnError onError;

    public SecretsResolver(List<SecretsProvider> chain) {
        this(chain, OnError.CONTINUE);
    }

    public SecretsResolver(List<SecretsProvider> chain, OnError onError) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(onError, "onError");
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("chain must not be empty");
        }
        this.chain = List.copyOf(chain);
        this.onError = onError;
    }

    /** The configured chain, in resolution order. */
    public List<SecretsProvider> chain() {
        return chain;
    }

    /** The configured error-handling mode. */
    public OnError onError() {
        return onError;
    }

    /**
     * Resolve a single key, walking the chain. See class-level docs for
     * behavior on {@link SecretResolution.ProviderError ProviderError}.
     */
    public SecretResolution resolve(String key) {
        Objects.requireNonNull(key, "key");
        SecretResolution.ProviderError firstError = null;
        for (SecretsProvider provider : chain) {
            try {
                Optional<String> value = provider.get(key);
                if (value.isPresent()) {
                    return new SecretResolution.Resolved(key, value.get(), provider.name());
                }
            } catch (RuntimeException e) {
                SecretResolution.ProviderError err =
                        new SecretResolution.ProviderError(key, provider.name(), e);
                if (onError == OnError.FAIL) {
                    return err;
                }
                if (firstError == null) {
                    firstError = err;
                }
                // CONTINUE — record and try next provider
            }
        }
        return firstError != null ? firstError : new SecretResolution.Missing(key);
    }

    /**
     * Convenience: resolve and return the value if {@link
     * SecretResolution.Resolved Resolved}, otherwise empty.
     */
    public Optional<String> getValue(String key) {
        return resolve(key) instanceof SecretResolution.Resolved r
                ? Optional.of(r.value())
                : Optional.empty();
    }

    /**
     * Get all secrets matching a prefix, merging results across the
     * chain. Providers earlier in the chain win for duplicate keys
     * (matches the resolution order of {@link #resolve(String)}).
     */
    public Map<String, String> getAll(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        // Walk in reverse so earlier-in-chain overrides later-in-chain
        // when we put-all into the accumulator.
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>();
        List<SecretsProvider> reversed = new ArrayList<>(chain);
        java.util.Collections.reverse(reversed);
        for (SecretsProvider provider : reversed) {
            try {
                merged.putAll(provider.getAll(prefix));
            } catch (RuntimeException e) {
                if (onError == OnError.FAIL) {
                    throw e;
                }
                // CONTINUE — skip this provider's entries
            }
        }
        return merged;
    }

    /** Refresh all providers in the chain. */
    public void refresh() {
        for (SecretsProvider provider : chain) {
            try {
                provider.refresh();
            } catch (RuntimeException e) {
                if (onError == OnError.FAIL) {
                    throw e;
                }
                // CONTINUE — best-effort refresh
            }
        }
    }
}
