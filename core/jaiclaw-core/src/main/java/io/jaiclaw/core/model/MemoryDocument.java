package io.jaiclaw.core.model;

import java.time.Instant;

/**
 * Bounded markdown blob spliced into the system prompt at session start.
 * Modeled after hermes-agent's {@code MEMORY.md} (AGENT) and {@code USER.md}
 * (PEER) concepts, with JaiClaw's {@link MemoryScope#TENANT} variant for
 * org-wide institutional knowledge.
 *
 * <p>The {@link #content()} field is rendered verbatim into the system
 * prompt. Section headings inside the body are addressed by the
 * {@code memory} agent tool's add/replace/remove actions but not parsed
 * at the record level.
 *
 * <p>The {@link #charBudget()} field is the hard maximum length of
 * {@code content()}. Writes that would exceed the budget MUST raise
 * {@link io.jaiclaw.core.agent.MemoryOverflowException} so the LLM is
 * forced to consolidate in-turn (hermes' "error-as-control-flow" pattern,
 * analysis §8.1).
 *
 * <p>The {@link #version()} field enables optimistic CAS — stale writes
 * are rejected by the store layer.
 *
 * <p>Nullability rules enforced by the compact constructor:
 * <ul>
 *   <li>{@link MemoryScope#TENANT} — {@code agentId} and {@code peerId} are null</li>
 *   <li>{@link MemoryScope#AGENT} — {@code agentId} required, {@code peerId} is null</li>
 *   <li>{@link MemoryScope#PEER} — both {@code agentId} and {@code peerId} required</li>
 * </ul>
 */
public record MemoryDocument(
        MemoryScope scope,
        String tenantId,
        String agentId,
        String peerId,
        String content,
        int charBudget,
        Instant updatedAt,
        long version
) {

    public MemoryDocument {
        if (scope == null) throw new IllegalArgumentException("scope is required");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        switch (scope) {
            case TENANT -> {
                if (agentId != null) {
                    throw new IllegalArgumentException("TENANT-scope MemoryDocument must have null agentId");
                }
                if (peerId != null) {
                    throw new IllegalArgumentException("TENANT-scope MemoryDocument must have null peerId");
                }
            }
            case AGENT -> {
                if (agentId == null || agentId.isBlank()) {
                    throw new IllegalArgumentException("AGENT-scope MemoryDocument requires a non-blank agentId");
                }
                if (peerId != null) {
                    throw new IllegalArgumentException("AGENT-scope MemoryDocument must have null peerId");
                }
            }
            case PEER -> {
                if (agentId == null || agentId.isBlank()) {
                    throw new IllegalArgumentException("PEER-scope MemoryDocument requires a non-blank agentId");
                }
                if (peerId == null || peerId.isBlank()) {
                    throw new IllegalArgumentException("PEER-scope MemoryDocument requires a non-blank peerId");
                }
            }
        }
        if (charBudget <= 0) {
            throw new IllegalArgumentException("charBudget must be positive, got " + charBudget);
        }
        if (content == null) content = "";
        if (updatedAt == null) updatedAt = Instant.now();
    }

    /** Create a fresh TENANT-scope MemoryDocument with version 0. */
    public static MemoryDocument forTenant(String tenantId, String content, int charBudget) {
        return new MemoryDocument(MemoryScope.TENANT, tenantId, null, null,
                content, charBudget, Instant.now(), 0L);
    }

    /** Create a fresh AGENT-scope MemoryDocument with version 0. */
    public static MemoryDocument forAgent(String tenantId, String agentId, String content, int charBudget) {
        return new MemoryDocument(MemoryScope.AGENT, tenantId, agentId, null,
                content, charBudget, Instant.now(), 0L);
    }

    /** Create a fresh PEER-scope MemoryDocument with version 0. */
    public static MemoryDocument forPeer(String tenantId, String agentId, String peerId,
                                          String content, int charBudget) {
        return new MemoryDocument(MemoryScope.PEER, tenantId, agentId, peerId,
                content, charBudget, Instant.now(), 0L);
    }

    /** Returns a copy with new content and a bumped version. */
    public MemoryDocument withContent(String newContent) {
        return new MemoryDocument(scope, tenantId, agentId, peerId,
                newContent, charBudget, Instant.now(), version + 1);
    }
}
