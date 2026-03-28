package io.jaiclaw.tools.security;

import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory store for active handshake sessions.
 * In multi-tenant mode, validates tenant context on session retrieval.
 */
public class HandshakeSessionStore {

    private final ConcurrentMap<String, HandshakeSession> sessions = new ConcurrentHashMap<>();

    /**
     * Create a new handshake session with a random ID.
     * Stamps the current tenantId from {@link TenantContextHolder} if available.
     */
    public HandshakeSession create() {
        String id = UUID.randomUUID().toString();
        HandshakeSession session = new HandshakeSession(id);
        TenantContext ctx = TenantContextHolder.get();
        if (ctx != null) {
            session.setTenantId(ctx.getTenantId());
        }
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
     * Validates tenant context — returns empty if the session belongs to a different tenant.
     */
    public Optional<HandshakeSession> get(String handshakeId) {
        HandshakeSession session = sessions.get(handshakeId);
        if (session == null) return Optional.empty();
        if (!isTenantMatch(session)) return Optional.empty();
        return Optional.of(session);
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
     * Validates tenant context on the matched session.
     */
    public Optional<HandshakeSession> findByToken(String sessionToken) {
        if (sessionToken == null) return Optional.empty();
        return sessions.values().stream()
                .filter(s -> s.isCompleted() && sessionToken.equals(s.getSessionToken()))
                .filter(this::isTenantMatch)
                .findFirst();
    }

    /**
     * Number of active sessions.
     */
    public int size() {
        return sessions.size();
    }

    private boolean isTenantMatch(HandshakeSession session) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null || session.getTenantId() == null) return true;
        return ctx.getTenantId().equals(session.getTenantId());
    }
}
