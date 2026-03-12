package io.jclaw.agent.session;

import io.jclaw.core.model.Message;
import io.jclaw.core.model.Session;
import io.jclaw.core.model.SessionState;
import io.jclaw.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session manager. Manages session lifecycle for the agent runtime.
 * Sessions are scoped to the current tenant via {@link TenantContextHolder}.
 * Future phases will add persistent storage backends via a SessionStore SPI.
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session getOrCreate(String sessionKey, String agentId) {
        return sessions.computeIfAbsent(sessionKey, k -> {
            String tenantId = resolveTenantId();
            return Session.create(UUID.randomUUID().toString(), k, agentId, tenantId);
        });
    }

    public void appendMessage(String sessionKey, Message message) {
        sessions.computeIfPresent(sessionKey,
                (k, session) -> session.withMessage(message));
    }

    public Optional<Session> get(String sessionKey) {
        Session session = sessions.get(sessionKey);
        if (session == null) return Optional.empty();
        // Enforce tenant isolation if a tenant context is active
        String currentTenant = resolveTenantId();
        if (currentTenant != null && session.tenantId() != null
                && !currentTenant.equals(session.tenantId())) {
            log.warn("Tenant mismatch: session {} belongs to tenant {}, current tenant is {}",
                    sessionKey, session.tenantId(), currentTenant);
            return Optional.empty();
        }
        return Optional.of(session);
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

    /**
     * List sessions for the current tenant. If no tenant context is active,
     * returns all sessions (backward-compatible single-tenant behavior).
     */
    public List<Session> listSessions() {
        String currentTenant = resolveTenantId();
        if (currentTenant == null) {
            return List.copyOf(sessions.values());
        }
        return sessions.values().stream()
                .filter(s -> currentTenant.equals(s.tenantId()))
                .toList();
    }

    public List<Session> listActiveSessions() {
        return listSessions().stream()
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
        return listSessions().size();
    }

    private String resolveTenantId() {
        var ctx = TenantContextHolder.get();
        return ctx != null ? ctx.getTenantId() : null;
    }
}
