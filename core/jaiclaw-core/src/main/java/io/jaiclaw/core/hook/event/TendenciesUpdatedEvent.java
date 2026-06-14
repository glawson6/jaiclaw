package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired by the {@code jaiclaw-agentmind-tendencies} extension after a
 * learning pass writes a fresh {@code Tendencies} record. Promotes the
 * existing log line to a first-class {@link HookEvent} subtype so plugins
 * (audit, dashboard, downstream caches) can subscribe without piggybacking
 * on a generic event.
 *
 * <p>{@link HookEvent#agentId()} carries the agent whose transcript drove
 * the learning pass; {@link HookEvent#sessionKey()} carries the session
 * the pass observed.
 *
 * <p>Plan task 4.7 — first-class HookEvent audit; admits Soul/Memory/
 * Tendencies events alongside the kanban precedent.
 *
 * @param agentId          agent the learning pass observed
 * @param sessionKey       session the learning pass observed
 * @param timestamp        when the write happened
 * @param tenantId         tenant the Tendencies belong to
 * @param canonicalUserId  user the Tendencies belong to
 * @param version          new Tendencies version after the write (≥ 1)
 * @param provider         which learning provider fired
 *                         ({@code deterministic|local-llm|honcho|...})
 */
@Experimental
public record TendenciesUpdatedEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        String tenantId,
        String canonicalUserId,
        long version,
        String provider
) implements HookEvent {

    public static TendenciesUpdatedEvent of(String tenantId, String canonicalUserId,
                                            String agentId, String sessionKey,
                                            long version, String provider) {
        return new TendenciesUpdatedEvent(agentId, sessionKey, Instant.now(),
                tenantId, canonicalUserId, version, provider);
    }
}
