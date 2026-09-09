package io.jaiclaw.gateway.admin;

import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.hook.event.EmergencyStopEvent;
import io.jaiclaw.core.ops.EmergencyStop;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator endpoint for the global emergency stop, at
 * {@code /actuator/jaiclaw-estop}.
 *
 * <ul>
 *   <li>{@code GET} reports whether the stop is engaged, why, and since when.</li>
 *   <li>{@code POST {"engaged": true, "reason": "..."}} engages it.</li>
 *   <li>{@code POST {"engaged": false}} releases it.</li>
 * </ul>
 *
 * <p>This endpoint is a <em>mutating</em> operational control: anyone who can
 * reach it can pause every agent in the deployment. Adopters must front
 * {@code /actuator/**} with the same authentication they use for the rest of
 * their admin surface — the endpoint performs no authorization of its own,
 * matching the existing actuator endpoints in this codebase.
 *
 * <p>{@link EmergencyStopEvent} is fired here rather than in
 * {@link EmergencyStop} itself, because the readers poll the sentinel on every
 * inbound message and firing there would flood the hook bus.
 *
 * <p>Phase 1 of the 1.2.0 plan.
 */
@Endpoint(id = "jaiclaw-estop")
public class EstopActuatorEndpoint {

    private static final Logger log = LoggerFactory.getLogger(EstopActuatorEndpoint.class);

    private final EmergencyStop emergencyStop;

    @Nullable
    private final AgentHookDispatcher hooks;

    public EstopActuatorEndpoint(EmergencyStop emergencyStop) {
        this(emergencyStop, null);
    }

    public EstopActuatorEndpoint(EmergencyStop emergencyStop, @Nullable AgentHookDispatcher hooks) {
        this.emergencyStop = emergencyStop;
        this.hooks = hooks;
    }

    /** {@code GET /actuator/jaiclaw-estop} — current state. */
    @ReadOperation
    public Map<String, Object> status() {
        EmergencyStop.Status status = emergencyStop.status();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("engaged", status.engaged());
        body.put("reason", status.reason());
        body.put("engagedAt", status.engagedAt() == null ? null : status.engagedAt().toString());
        body.put("sentinel", emergencyStop.sentinelPath().toString());
        return body;
    }

    /**
     * {@code POST /actuator/jaiclaw-estop} — engage or release.
     *
     * @param engaged true to engage, false to release; defaults to engaging so a
     *                bare POST is a pause rather than a silent no-op
     * @param reason  operator reason recorded in the sentinel; ignored on release
     */
    @WriteOperation
    public Map<String, Object> set(@Nullable Boolean engaged, @Nullable String reason) {
        boolean engage = engaged == null || engaged;
        try {
            if (engage) {
                emergencyStop.engage(reason);
                log.warn("ESTOP ENGAGED via actuator — reason: {}", reason == null ? "(none given)" : reason);
                fire(EmergencyStopEvent.engaged(reason, "actuator"));
            } else {
                boolean released = emergencyStop.release();
                log.warn("ESTOP released via actuator (sentinel {})", released ? "removed" : "already absent");
                fire(EmergencyStopEvent.released("actuator"));
            }
        } catch (IOException e) {
            // Surfacing this matters: an operator who thinks they paused the fleet
            // and did not must find out immediately.
            log.error("Failed to {} the emergency stop at {}",
                    engage ? "engage" : "release", emergencyStop.sentinelPath(), e);
            throw new UncheckedIOException("Emergency stop update failed", e);
        }
        return status();
    }

    private void fire(EmergencyStopEvent event) {
        if (hooks == null) return;
        try {
            hooks.fireVoid(event);
        } catch (RuntimeException e) {
            // A misbehaving hook must never prevent an operator from pausing.
            log.warn("EmergencyStopEvent hook failed", e);
        }
    }
}
