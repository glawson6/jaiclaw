package io.jaiclaw.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Immutable audit event recording an action taken within the system.
 *
 * <p>0.9.4 (T1-2): five optional compliance fields — {@code lawfulBasis},
 * {@code dataCategories}, {@code recipients}, {@code retentionDays},
 * {@code consentToken}. They exist so downstream code can reconstruct a
 * GDPR Art. 30 Record of Processing (RoPA) and HIPAA §164.312(b) access
 * log from the audit trail without side-band state. All five are optional
 * (null / empty by default); existing callers who don't set them see zero
 * behavioral change.
 *
 * @param id             unique event identifier
 * @param timestamp      when the event occurred
 * @param tenantId       tenant that triggered the event (nullable for system events)
 * @param actor          the actor who performed the action (user, agent, system)
 * @param action         the action performed (e.g. "message.sent", "tool.executed", "session.created")
 * @param resource       the resource affected (e.g. session key, tool name)
 * @param outcome        result: SUCCESS, FAILURE, DENIED
 * @param details        additional structured data
 * @param lawfulBasis    optional GDPR Art. 6 lawful basis at the time of processing
 * @param dataCategories optional set of data-category labels (e.g. "user_utterance",
 *                       "session_context", "attachment", "redacted.ssn")
 * @param recipients     optional set of recipient labels (e.g. "anthropic-bedrock-us-east-1")
 *                       for GDPR Art. 44 cross-border transfer tracking
 * @param retentionDays  optional snapshot of the retention policy in force
 * @param consentToken   optional reference to a ConsentManager record (T2-5)
 */
public record AuditEvent(
        String id,
        Instant timestamp,
        String tenantId,
        String actor,
        String action,
        String resource,
        Outcome outcome,
        Map<String, Object> details,
        String lawfulBasis,
        Set<String> dataCategories,
        Set<String> recipients,
        Integer retentionDays,
        String consentToken
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
        // Compliance fields default to null / empty. Set defensively-immutable
        // copies of the collections so callers can't mutate them after the fact.
        if (dataCategories == null) {
            dataCategories = Set.of();
        } else {
            dataCategories = Set.copyOf(dataCategories);
        }
        if (recipients == null) {
            recipients = Set.of();
        } else {
            recipients = Set.copyOf(recipients);
        }
        // lawfulBasis + retentionDays + consentToken stay as-is (nullable scalars).
    }

    /**
     * Backward-compatible 8-arg constructor for pre-T1-2 callers. All
     * compliance fields default to null / empty. Existing consumers built
     * against 0.9.3 continue to compile without changes.
     */
    public AuditEvent(String id, Instant timestamp, String tenantId, String actor,
                      String action, String resource, Outcome outcome, Map<String, Object> details) {
        this(id, timestamp, tenantId, actor, action, resource, outcome, details,
                null, null, null, null, null);
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
        // Compliance
        private String lawfulBasis;
        private Set<String> dataCategories;
        private Set<String> recipients;
        private Integer retentionDays;
        private String consentToken;

        public Builder id(String id) { this.id = id; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder actor(String actor) { this.actor = actor; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder resource(String resource) { this.resource = resource; return this; }
        public Builder outcome(Outcome outcome) { this.outcome = outcome; return this; }
        public Builder details(Map<String, Object> details) { this.details = details; return this; }

        /** GDPR Art. 6 lawful basis at time of processing. Optional. */
        public Builder lawfulBasis(String lawfulBasis) { this.lawfulBasis = lawfulBasis; return this; }

        /** GDPR Art. 30 data categories (e.g. {@code "user_utterance"}). Optional. */
        public Builder dataCategories(Set<String> dataCategories) { this.dataCategories = dataCategories; return this; }

        /** GDPR Art. 30 / Art. 44 recipients (e.g. {@code "anthropic-bedrock-us-east-1"}). Optional. */
        public Builder recipients(Set<String> recipients) { this.recipients = recipients; return this; }

        /** Snapshot of the retention policy in force at this event's time. Optional. */
        public Builder retentionDays(Integer retentionDays) { this.retentionDays = retentionDays; return this; }

        /** Reference to a ConsentManager record (T2-5). Optional. */
        public Builder consentToken(String consentToken) { this.consentToken = consentToken; return this; }

        public AuditEvent build() {
            return new AuditEvent(id, timestamp, tenantId, actor, action, resource, outcome, details,
                    lawfulBasis, dataCategories, recipients, retentionDays, consentToken);
        }
    }
}
