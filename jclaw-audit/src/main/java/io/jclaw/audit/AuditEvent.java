package io.jclaw.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable audit event recording an action taken within the system.
 *
 * @param id         unique event identifier
 * @param timestamp  when the event occurred
 * @param tenantId   tenant that triggered the event (nullable for system events)
 * @param actor      the actor who performed the action (user, agent, system)
 * @param action     the action performed (e.g. "message.sent", "tool.executed", "session.created")
 * @param resource   the resource affected (e.g. session key, tool name)
 * @param outcome    result: SUCCESS, FAILURE, DENIED
 * @param details    additional structured data
 */
public record AuditEvent(
        String id,
        Instant timestamp,
        String tenantId,
        String actor,
        String action,
        String resource,
        Outcome outcome,
        Map<String, Object> details
) {
    public enum Outcome { SUCCESS, FAILURE, DENIED }

    public AuditEvent {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (timestamp == null) timestamp = Instant.now();
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        if (outcome == null) outcome = Outcome.SUCCESS;
        if (details == null) details = Map.of();
        if (actor == null) actor = "system";
        if (resource == null) resource = "";
    }

    public static AuditEvent success(String id, String tenantId, String actor, String action, String resource) {
        return new AuditEvent(id, Instant.now(), tenantId, actor, action, resource, Outcome.SUCCESS, Map.of());
    }

    public static AuditEvent failure(String id, String tenantId, String actor, String action, String resource, String reason) {
        return new AuditEvent(id, Instant.now(), tenantId, actor, action, resource, Outcome.FAILURE,
                Map.of("reason", reason));
    }
}
