package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.model.SoulScope;

import java.time.Instant;

/**
 * Fired by the {@code jaiclaw-agentmind-soul} extension after every accepted
 * Soul write (operator REST, agent tool, or programmatic via
 * {@code SoulProvider.saveSoul()}). Promotes the existing log line to a
 * first-class {@link HookEvent} subtype so plugins can subscribe to Soul
 * mutations for audit, dashboards, or cache invalidation without
 * piggybacking on a generic event.
 *
 * <p>{@link HookEvent#agentId()} carries the {@code agentId} for AGENT-scope
 * writes, or {@code "*"} for TENANT-scope writes (which apply to every
 * agent in the tenant). {@link HookEvent#sessionKey()} is {@code null}
 * because Soul writes are session-independent.
 *
 * <p>Plan task 4.7 — first-class HookEvent audit; admits Soul/Memory/
 * Tendencies events alongside the kanban precedent.
 *
 * @param agentId   agent the write targets; {@code "*"} for tenant scope
 * @param sessionKey always {@code null} — Soul is not session-scoped
 * @param timestamp when the write happened
 * @param scope     {@link SoulScope#AGENT} or {@link SoulScope#TENANT}
 * @param tenantId  tenant the Soul belongs to
 * @param version   new Soul version after the write (≥ 1)
 * @param actor     who fired the write ({@code operator|agent|system|null})
 */
@Experimental
public record SoulUpdatedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        SoulScope scope,
        String tenantId,
        long version,
        String actor
) implements HookEvent {

    public static SoulUpdatedEvent ofAgent(String tenantId, String agentId, long version, String actor) {
        return new SoulUpdatedEvent(agentId, null, Instant.now(),
                SoulScope.AGENT, tenantId, version, actor);
    }

    public static SoulUpdatedEvent ofTenant(String tenantId, long version, String actor) {
        return new SoulUpdatedEvent("*", null, Instant.now(),
                SoulScope.TENANT, tenantId, version, actor);
    }
}
