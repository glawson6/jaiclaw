package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired when the global emergency stop is engaged or released.
 *
 * <p>Fired only from the paths that <em>mutate</em> the sentinel — the actuator
 * endpoint and the CLI {@code pause}/{@code resume} commands — never from the
 * readers that poll it. Readers run on every inbound message and every cron
 * tick; firing there would flood the hook bus.
 *
 * <p>Phase 1 of the 1.2.0 plan (runtime guards).
 *
 * @param agentId    always {@code "*"} — the stop is global, not per-agent
 * @param sessionKey always {@code null} — the stop is not session-scoped
 * @param timestamp  when the state changed
 * @param engaged    {@code true} on engage, {@code false} on release
 * @param reason     operator-supplied reason on engage; {@code null} on release
 * @param actor      who changed it ({@code operator|cli|actuator|system}), may be null
 */
@Experimental
public record EmergencyStopEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        boolean engaged,
        String reason,
        String actor
) implements HookEvent {

    public static EmergencyStopEvent engaged(String reason, String actor) {
        return new EmergencyStopEvent("*", null, Instant.now(), true, reason, actor);
    }

    public static EmergencyStopEvent released(String actor) {
        return new EmergencyStopEvent("*", null, Instant.now(), false, null, actor);
    }
}
