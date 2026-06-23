package io.jaiclaw.core.secrets;

import io.jaiclaw.core.api.Stable;

import java.util.Map;
import java.util.Optional;

/**
 * SPI for resolving secrets from a backing store.
 *
 * <p>Implementations are typically registered as Spring beans and
 * consulted by {@link SecretsResolver} in a configured order. The chain
 * order is set via {@code jaiclaw.secrets.chain} when {@code
 * jaiclaw.secrets.provider=composite}.
 *
 * <p>0.9.2 secrets baseline.
 */
@Stable
public interface SecretsProvider {

    /**
     * Look up a single secret by its logical key.
     *
     * <p>Implementations should return {@link Optional#empty()} for
     * "key not found" and throw only for transient infrastructure
     * failures (e.g., backing-store unreachable). The resolver
     * translates "key not found" into the next provider in the chain
     * and throws into a {@link SecretResolution.ProviderError} so
     * callers can decide whether to fail or continue.
     */
    Optional<String> get(String key);

    /**
     * Look up all secrets whose keys begin with the given prefix.
     *
     * <p>Keys in the returned map have the prefix stripped. For
     * example, calling {@code getAll("tenant-acme/")} against a
     * provider holding {@code tenant-acme/anthropic-api-key} returns a
     * map with the entry {@code anthropic-api-key -> ...}.
     *
     * <p>Implementations that cannot efficiently enumerate keys may
     * return an empty map and rely on per-key {@link #get(String)}
     * lookups instead.
     */
    Map<String, String> getAll(String prefix);

    /**
     * Provider name used for telemetry and chain configuration.
     *
     * <p>Conventionally the short backing-store name in lowercase:
     * {@code "env"}, {@code "file"}, {@code "onepassword"}, etc.
     */
    String name();

    /**
     * Optional refresh hook for providers that cache backing-store
     * state. The default implementation is a no-op.
     */
    default void refresh() {
        /* no-op */
    }
}
