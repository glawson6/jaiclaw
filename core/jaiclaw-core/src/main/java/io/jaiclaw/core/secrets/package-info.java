/**
 * Secrets SPI — pluggable resolution of secret values from arbitrary
 * backing stores (environment, files, vaults).
 *
 * <p>The SPI is consulted by JaiClaw's Spring auto-configuration to
 * populate the {@code Environment} with secrets resolved from the
 * configured provider chain, so existing {@code ${VAR}} references in
 * {@code application.yml} continue to work — adopters change one
 * property ({@code jaiclaw.secrets.provider}) and their existing
 * references re-resolve through the chain.
 *
 * <p>{@link io.jaiclaw.core.secrets.SecretsProvider},
 * {@link io.jaiclaw.core.secrets.SecretsResolver},
 * {@link io.jaiclaw.core.secrets.SecretReference}, and
 * {@link io.jaiclaw.core.secrets.SecretResolution} are stable surfaces
 * under the 0.9.2 secrets baseline. The package is {@link
 * org.jspecify.annotations.NullMarked} — every method return is
 * non-null unless explicitly annotated {@link
 * org.jspecify.annotations.Nullable}.
 */
@org.jspecify.annotations.NullMarked
package io.jaiclaw.core.secrets;
