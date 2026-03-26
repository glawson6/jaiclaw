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
}
