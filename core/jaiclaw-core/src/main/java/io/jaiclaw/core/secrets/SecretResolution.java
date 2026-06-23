package io.jaiclaw.core.secrets;

import io.jaiclaw.core.api.Stable;

/**
 * Result of a single secret-resolution attempt.
 *
 * <p>This is a sealed sum type: a resolution either yields a value,
 * reports that no provider in the chain had the key, or surfaces a
 * provider-level error. {@link SecretsResolver} returns one of these
 * to give callers the full picture without throwing for the common
 * "key not configured" case.
 *
 * <p>0.9.2 secrets baseline.
 */
@Stable
public sealed interface SecretResolution
        permits SecretResolution.Resolved,
                SecretResolution.Missing,
                SecretResolution.ProviderError {

    /** The logical key that was looked up. */
    String key();

    /** A successful resolution. */
    record Resolved(String key, String value, String providerName)
            implements SecretResolution {}

    /** No provider in the chain had the key. */
    record Missing(String key) implements SecretResolution {}

    /**
     * A provider threw while looking up the key. The chain may have
     * continued past this error (see {@code jaiclaw.secrets.chain-on-error});
     * if so, a later provider may have produced {@link Resolved} and
     * this record is for diagnostic purposes only.
     */
    record ProviderError(String key, String providerName, Throwable cause)
            implements SecretResolution {}
}
