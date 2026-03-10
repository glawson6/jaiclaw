package io.jclaw.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Session(
        String id,
        String sessionKey,
        String agentId,
        Instant createdAt,
        Instant lastActiveAt,
        SessionState state,
        List<Message> messages
) {
    public Session {
        messages = Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public static Session create(String id, String sessionKey, String agentId) {
        var now = Instant.now();
        return new Session(id, sessionKey, agentId, now, now, SessionState.ACTIVE, List.of());
    }

    public Session withMessage(Message message) {
        var updated = new ArrayList<>(messages);
        updated.add(message);
        return new Session(id, sessionKey, agentId, createdAt, Instant.now(), state, updated);
    }

    public Session withState(SessionState newState) {
        return new Session(id, sessionKey, agentId, createdAt, Instant.now(), newState, messages);
    }
}
