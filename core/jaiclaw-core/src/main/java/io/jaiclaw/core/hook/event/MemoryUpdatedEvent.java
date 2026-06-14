package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the {@code jaiclaw-agentmind-memory} extension after every
 * accepted Memory blob write (agent tool, operator REST, or compaction
 * job). Promotes the existing log line to a first-class {@link HookEvent}
 * subtype so plugins can subscribe to Memory mutations for audit,
 * dashboards, or cache invalidation.
 *
 * <p>{@link HookEvent#agentId()} carries the {@code agentId} for AGENT/PEER
 * scope writes, or {@code "*"} for TENANT-scope writes.
 * {@link HookEvent#sessionKey()} carries the originating session when the
 * write came from an in-session agent tool call; otherwise {@code null}.
 *
 * <p>Plan task 4.7 — first-class HookEvent audit; admits Soul/Memory/
 * Tendencies events alongside the kanban precedent.
 *
 * @param agentId          agent the write targets; {@code "*"} for tenant scope
 * @param sessionKey       session that triggered the write, or {@code null}
 * @param timestamp        when the write happened
 * @param scope            {@code "AGENT" | "PEER" | "TENANT"}
 * @param tenantId         tenant the Memory belongs to
 * @param canonicalUserId  user the Memory belongs to (PEER scope), or {@code null}
 * @param version          new Memory version after the write (≥ 1)
 * @param actor            who fired the write ({@code agent|operator|compaction|system})
 */
@Experimental
public record MemoryUpdatedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String scope,
        String tenantId,
        String canonicalUserId,
        long version,
        String actor
) implements HookEvent {

    public static MemoryUpdatedEvent ofPeer(String tenantId, String agentId, String canonicalUserId,
                                            String sessionKey, long version, String actor) {
        return new MemoryUpdatedEvent(agentId, sessionKey, Instant.now(),
                "PEER", tenantId, canonicalUserId, version, actor);
    }

    public static MemoryUpdatedEvent ofAgent(String tenantId, String agentId,
                                             long version, String actor) {
        return new MemoryUpdatedEvent(agentId, null, Instant.now(),
                "AGENT", tenantId, null, version, actor);
    }

    public static MemoryUpdatedEvent ofTenant(String tenantId, long version, String actor) {
        return new MemoryUpdatedEvent("*", null, Instant.now(),
                "TENANT", tenantId, null, version, actor);
    }
}
