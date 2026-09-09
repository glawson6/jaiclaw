package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Something the background reviewer suggests changing, awaiting a decision.
 *
 * <p>Sealed: a proposal is one of memory, a new skill, or a patch to an existing
 * skill. Each carries the tenant it belongs to and the session that produced it,
 * so an operator reviewing a queue can always answer "where did this come from".
 *
 * <p>{@link #contentHash()} is what makes a proposal <em>idempotent</em>: a
 * reviewer that keeps noticing the same pattern across sessions produces the same
 * hash, and the store drops the duplicate instead of growing an unreadable queue.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public sealed interface Proposal
        permits MemoryProposal, SkillProposal, SkillPatchProposal {

    /** Stable id, assigned at submission. */
    String id();

    /** Tenant this proposal belongs to; {@code "default"} in SINGLE mode. */
    String tenantId();

    /** Session that produced it, for provenance. */
    String originSessionKey();

    /** When the reviewer created it. */
    Instant createdAt();

    /** Current lifecycle state. */
    ProposalState state();

    /** What it would change. */
    ProposalKind kind();

    /** One-line summary shown in a review queue. */
    String summary();

    /**
     * Hash of the semantic content (not the id or timestamp), used to dedupe
     * repeat suggestions across sessions.
     */
    String contentHash();

    /** A copy in the given state. */
    Proposal withState(ProposalState newState);

    /** SHA-256 of the given parts, hex-encoded. Shared by all implementations. */
    static String hashOf(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                // Length-delimit so ("ab","c") and ("a","bc") hash differently.
                digest.update((byte) 0x1f);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
