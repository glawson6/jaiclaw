package io.jaiclaw.learning.util;

import io.jaiclaw.core.api.Experimental;

import java.util.Locale;

/**
 * Turns identifiers into safe single path segments.
 *
 * <p>Shared by the proposal store, the ledger and the skill writer, all of which
 * build per-tenant directories from values that may be operator- or
 * model-supplied. Centralised so the traversal defence is defined once rather
 * than reimplemented — subtly differently — in three places.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public final class PathSegments {

    private static final int MAX_LENGTH = 128;

    private PathSegments() {}

    /**
     * Reduces {@code raw} to lowercase {@code [a-z0-9._-]}, collapsing anything
     * else to an underscore, so the result can never traverse out of its parent.
     * {@code .} and {@code ..} are rewritten rather than passed through.
     *
     * @return a safe segment; {@code "default"} for null or blank input
     */
    public static String safe(String raw) {
        if (raw == null || raw.isBlank()) return "default";
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (cleaned.equals(".") || cleaned.equals("..")) return "_" + cleaned;
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
