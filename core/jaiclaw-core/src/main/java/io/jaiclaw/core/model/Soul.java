package io.jaiclaw.core.model;

import java.time.Instant;

/**
 * Markdown personality / identity overlay spliced into the agent's system prompt.
 *
 * <p>Modeled after the {@code SOUL.md} concept in hermes-agent. Two scopes coexist:
 * <ul>
 *   <li>{@link SoulScope#TENANT} — one record per {@code tenantId} representing the
 *       org-wide operator-authored voice. {@code agentId} is {@code null}.</li>
 *   <li>{@link SoulScope#AGENT} — one record per {@code (tenantId, agentId)}, the
 *       per-agent personality that the {@code soul} agent tool can mutate.</li>
 * </ul>
 *
 * <p>The {@link #markdown()} field is rendered verbatim into the system prompt;
 * section headings (e.g. {@code # Identity}, {@code # Style}) are addressed by the
 * {@code soul} tool's add/replace/remove actions and not parsed at the record level.
 *
 * <p>The {@link #version()} field enables optimistic CAS — stale writes are
 * rejected by the store layer.
 *
 * @see SoulScope
 */
public record Soul(
        SoulScope scope,
        String tenantId,
        String agentId,
        String markdown,
        Instant lastModified,
        long version
) {

    /**
     * Compact constructor enforcing the {@code agentId} nullability invariant.
     *
     * @throws IllegalArgumentException if {@code scope == TENANT} and {@code agentId != null},
     *         or if {@code scope == AGENT} and {@code agentId == null}
     */
    public Soul {
        if (scope == null) throw new IllegalArgumentException("scope is required");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (scope == SoulScope.TENANT && agentId != null) {
            throw new IllegalArgumentException("TENANT-scope Soul must have null agentId");
        }
        if (scope == SoulScope.AGENT && (agentId == null || agentId.isBlank())) {
            throw new IllegalArgumentException("AGENT-scope Soul requires a non-blank agentId");
        }
        if (markdown == null) markdown = "";
        if (lastModified == null) lastModified = Instant.now();
    }

    /** Create a fresh AGENT-scope Soul with version 0. */
    public static Soul forAgent(String tenantId, String agentId, String markdown) {
        return new Soul(SoulScope.AGENT, tenantId, agentId, markdown, Instant.now(), 0L);
    }

    /** Create a fresh TENANT-scope Soul with version 0. */
    public static Soul forTenant(String tenantId, String markdown) {
        return new Soul(SoulScope.TENANT, tenantId, null, markdown, Instant.now(), 0L);
    }

    /** Returns a copy with a new markdown body and a bumped version. */
    public Soul withMarkdown(String newMarkdown) {
        return new Soul(scope, tenantId, agentId, newMarkdown, Instant.now(), version + 1);
    }
}
