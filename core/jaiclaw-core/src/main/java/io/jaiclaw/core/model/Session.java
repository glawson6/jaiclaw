package io.jaiclaw.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Session(
        String id,
        String sessionKey,
        String agentId,
        String tenantId,
        Instant createdAt,
        Instant lastActiveAt,
        SessionState state,
        List<Message> messages
) {
    public Session {
        messages = Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public static Session create(String id, String sessionKey, String agentId) {
        return create(id, sessionKey, agentId, null);
    }

    public static Session create(String id, String sessionKey, String agentId, String tenantId) {
        Instant now = Instant.now();
        return new Session(id, sessionKey, agentId, tenantId, now, now, SessionState.ACTIVE, List.of());
    }

    public Session withMessage(Message message) {
        List<Message> updated = new ArrayList<>(messages);
        updated.add(message);
        return new Session(id, sessionKey, agentId, tenantId, createdAt, Instant.now(), state, updated);
    }

    public Session withState(SessionState newState) {
        return new Session(id, sessionKey, agentId, tenantId, createdAt, Instant.now(), newState, messages);
    }

    public Session withMessages(List<Message> newMessages) {
        return new Session(id, sessionKey, agentId, tenantId, createdAt, Instant.now(), state, newMessages);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String sessionKey;
        private String agentId;
        private String tenantId;
        private Instant createdAt;
        private Instant lastActiveAt;
        private SessionState state;
        private List<Message> messages = List.of();

        public Builder id(String id) { this.id = id; return this; }
        public Builder sessionKey(String sessionKey) { this.sessionKey = sessionKey; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder lastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; return this; }
        public Builder state(SessionState state) { this.state = state; return this; }
        public Builder messages(List<Message> messages) { this.messages = messages; return this; }

        public Session build() {
            return new Session(id, sessionKey, agentId, tenantId, createdAt, lastActiveAt, state, messages);
        }
    }
}
