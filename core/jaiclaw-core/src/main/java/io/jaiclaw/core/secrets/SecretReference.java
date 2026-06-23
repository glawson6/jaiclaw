package io.jaiclaw.core.secrets;

import io.jaiclaw.core.api.Stable;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A symbolic reference to a secret, in the form {@code
 * provider://vault/item/field}.
 *
 * <p>Used in configuration to point at secrets without hardcoding the
 * resolution path. For example, an {@code application.yml} entry can
 * carry {@code anthropic-api-key: onepassword://JaiClaw-Prod/anthropic/api-key}
 * and the resolver dispatches to the right provider.
 *
 * <p>The {@code vault} and {@code field} components are optional —
 * providers that don't have a vault concept (e.g., {@code env}) ignore
 * them. The minimum form is {@code provider://item}.
 *
 * <p>0.9.2 secrets baseline.
 */
@Stable
public record SecretReference(
        String provider,
        @Nullable String vault,
        String item,
        @Nullable String field) {

    public SecretReference {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(item, "item");
        if (provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (item.isBlank()) {
            throw new IllegalArgumentException("item must not be blank");
        }
    }

    /**
     * Parse a reference string of the form {@code provider://vault/item/field}.
     *
     * <p>Accepts shorter forms:
     * <ul>
     *   <li>{@code provider://item} — vault and field omitted</li>
     *   <li>{@code provider://item/field} — vault omitted</li>
     *   <li>{@code provider://vault/item} — field omitted</li>
     *   <li>{@code provider://vault/item/field} — full form</li>
     * </ul>
     *
     * <p>For three-segment paths, the segment ordering is ambiguous
     * (vault/item vs item/field). The convention adopted here: if there
     * are exactly two path segments, treat them as {@code item/field}
     * (the {@code env} provider has no vault concept and this is the
     * common case). For three segments, treat as {@code vault/item/field}.
     */
    public static SecretReference parse(String reference) {
        Objects.requireNonNull(reference, "reference");
        int scheme = reference.indexOf("://");
        if (scheme < 0) {
            throw new IllegalArgumentException(
                    "Secret reference must contain '://': " + reference);
        }
        String provider = reference.substring(0, scheme);
        String rest = reference.substring(scheme + 3);
        if (rest.isEmpty()) {
            throw new IllegalArgumentException(
                    "Secret reference must have a path after '://': " + reference);
        }
        String[] parts = rest.split("/");
        return switch (parts.length) {
            case 1 -> new SecretReference(provider, null, parts[0], null);
            case 2 -> new SecretReference(provider, null, parts[0], parts[1]);
            case 3 -> new SecretReference(provider, parts[0], parts[1], parts[2]);
            default -> throw new IllegalArgumentException(
                    "Secret reference has too many path segments: " + reference);
        };
    }

    /** Render the reference back to its canonical string form. */
    public String asString() {
        StringBuilder sb = new StringBuilder(provider).append("://");
        if (vault != null) {
            sb.append(vault).append('/');
        }
        sb.append(item);
        if (field != null) {
            sb.append('/').append(field);
        }
        return sb.toString();
    }
}
