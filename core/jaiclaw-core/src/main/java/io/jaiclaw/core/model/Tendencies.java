package io.jaiclaw.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * Per-user learned representation derived from observed transcript patterns.
 * Modeled after upstream hermes-agent's Honcho plugin (Peer Cards + structured
 * traits), with JaiClaw's {@link TendenciesScope#TENANT} variant added for
 * org-wide rollup (Phase 5).
 *
 * <p>Hybrid representation: {@link #peerCardMarkdown()} is the verbatim
 * markdown blob spliced into the user message as a {@code <tendencies-context>}
 * (or {@code <tenant-tendencies>}) block. {@link #traits()} is a structured
 * map populated by the same dialectic pass — consumed by dashboards, REST
 * clients, and analytics. Both surfaces are kept in sync by the
 * {@code TendenciesLearningProvider}.
 *
 * <p>Optimistic CAS via {@link #version()}. Stale writes are rejected by the
 * store layer with {@link io.jaiclaw.core.agent.StaleTendenciesVersionException}.
 *
 * <p>Nullability rules enforced by the compact constructor:
 * <ul>
 *   <li>{@link TendenciesScope#TENANT} — {@code canonicalUserId} is null</li>
 *   <li>{@link TendenciesScope#USER} — {@code canonicalUserId} required</li>
 * </ul>
 */
public record Tendencies(
        TendenciesScope scope,
        String tenantId,
        String canonicalUserId,
        String peerCardMarkdown,
        Map<String, String> traits,
        Instant updatedAt,
        Instant lastDialecticAt,
        long dialecticPasses,
        long version
) {

    public Tendencies {
        if (scope == null) throw new IllegalArgumentException("scope is required");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        switch (scope) {
            case TENANT -> {
                if (canonicalUserId != null) {
                    throw new IllegalArgumentException("TENANT-scope Tendencies must have null canonicalUserId");
                }
            }
            case USER -> {
                if (canonicalUserId == null || canonicalUserId.isBlank()) {
                    throw new IllegalArgumentException("USER-scope Tendencies requires a non-blank canonicalUserId");
                }
            }
        }
        if (peerCardMarkdown == null) peerCardMarkdown = "";
        if (traits == null) traits = Map.of();
        else traits = Map.copyOf(traits);
        if (updatedAt == null) updatedAt = Instant.now();
    }

    /** Create a fresh USER-scope Tendencies with version 0 and no dialectic history. */
    public static Tendencies forUser(String tenantId, String canonicalUserId,
                                      String peerCardMarkdown, Map<String, String> traits) {
        return new Tendencies(TendenciesScope.USER, tenantId, canonicalUserId,
                peerCardMarkdown, traits, Instant.now(), null, 0L, 0L);
    }

    /** Create a fresh TENANT-scope Tendencies with version 0 (Phase 5 rollup use). */
    public static Tendencies forTenant(String tenantId, String peerCardMarkdown,
                                        Map<String, String> traits) {
        return new Tendencies(TendenciesScope.TENANT, tenantId, null,
                peerCardMarkdown, traits, Instant.now(), null, 0L, 0L);
    }

    /**
     * Returns a copy with updated markdown + traits and a bumped version.
     * Stamps {@code lastDialecticAt} and increments {@code dialecticPasses}.
     */
    public Tendencies withDialecticResult(String newMarkdown, Map<String, String> newTraits) {
        Instant now = Instant.now();
        return new Tendencies(scope, tenantId, canonicalUserId,
                newMarkdown, newTraits, now, now, dialecticPasses + 1, version + 1);
    }
}
