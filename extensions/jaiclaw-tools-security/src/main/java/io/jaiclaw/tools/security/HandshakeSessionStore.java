package io.jaiclaw.tools.security;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory store for active handshake sessions.
 */
public class HandshakeSessionStore {

    private final ConcurrentMap<String, HandshakeSession> sessions = new ConcurrentHashMap<>();

    /**
     * Create a new handshake session with a random ID.
     */
    public HandshakeSession create() {
        String id = UUID.randomUUID().toString();
        HandshakeSession session = new HandshakeSession(id);
        sessions.put(id, session);
        return session;
    }

    /**
     * Store a session with a specific handshake ID (used by HTTP_CLIENT mode
     * where the server assigns the ID).
     */
    public void put(String handshakeId, HandshakeSession session) {
        sessions.put(handshakeId, session);
    }

    /**
     * Retrieve a session by handshake ID.
     */
    public Optional<HandshakeSession> get(String handshakeId) {
        return Optional.ofNullable(sessions.get(handshakeId));
    }

    /**
     * Retrieve a session or throw if not found.
     */
    public HandshakeSession require(String handshakeId) {
        return get(handshakeId).orElseThrow(
                () -> new IllegalArgumentException("Unknown handshake session: " + handshakeId));
    }

    /**
     * Remove a completed or expired session.
     */
    public boolean remove(String handshakeId) {
        return sessions.remove(handshakeId) != null;
    }

    /**
     * Find a completed session by its session token.
     * Used by protected tools to validate Bearer tokens.
     */
    public Optional<HandshakeSession> findByToken(String sessionToken) {
        if (sessionToken == null) return Optional.empty();
        return sessions.values().stream()
                .filter(s -> s.isCompleted() && sessionToken.equals(s.getSessionToken()))
                .findFirst();
    }

    /**
     * Number of active sessions.
     */
    public int size() {
        return sessions.size();
    }
}
