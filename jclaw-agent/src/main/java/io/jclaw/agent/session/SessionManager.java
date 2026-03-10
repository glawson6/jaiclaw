package io.jclaw.agent.session;

import io.jclaw.core.model.Message;
import io.jclaw.core.model.Session;
import io.jclaw.core.model.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session manager. Manages session lifecycle for the agent runtime.
 * Future phases will add persistent storage backends via a SessionStore SPI.
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session getOrCreate(String sessionKey, String agentId) {
        return sessions.computeIfAbsent(sessionKey,
                k -> Session.create(UUID.randomUUID().toString(), k, agentId));
    }

    public void appendMessage(String sessionKey, Message message) {
        sessions.computeIfPresent(sessionKey,
                (k, session) -> session.withMessage(message));
    }

    public Optional<Session> get(String sessionKey) {
        return Optional.ofNullable(sessions.get(sessionKey));
    }

    public Session transitionState(String sessionKey, SessionState newState) {
        return sessions.computeIfPresent(sessionKey,
                (k, session) -> {
                    log.debug("Session {} state: {} -> {}", sessionKey, session.state(), newState);
                    return session.withState(newState);
                });
    }

    public Session close(String sessionKey) {
        return transitionState(sessionKey, SessionState.CLOSED);
    }

    public void reset(String sessionKey) {
        sessions.remove(sessionKey);
    }

    public List<Session> listSessions() {
        return List.copyOf(sessions.values());
    }

    public List<Session> listActiveSessions() {
        return sessions.values().stream()
                .filter(s -> s.state() == SessionState.ACTIVE || s.state() == SessionState.IDLE)
                .toList();
    }

    public int messageCount(String sessionKey) {
        return get(sessionKey).map(s -> s.messages().size()).orElse(0);
    }

    public boolean exists(String sessionKey) {
        return sessions.containsKey(sessionKey);
    }

    public int sessionCount() {
        return sessions.size();
    }
}
