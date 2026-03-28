package io.jaiclaw.audit;

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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String tenantId;
        private String actor;
        private String action;
        private String resource;
        private Outcome outcome;
        private Map<String, Object> details;

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder actor(String actor) { this.actor = actor; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder resource(String resource) { this.resource = resource; return this; }
        public Builder outcome(Outcome outcome) { this.outcome = outcome; return this; }
        public Builder details(Map<String, Object> details) { this.details = details; return this; }

        public AuditEvent build() {
            return new AuditEvent(id, timestamp, tenantId, actor, action, resource, outcome, details);
        }
    }
}
