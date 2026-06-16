package io.jaiclaw.agent.session;

import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.hook.event.SessionEndedEvent;
import io.jaiclaw.core.hook.event.SessionStartedEvent;
import io.jaiclaw.core.model.Message;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.model.SessionState;
import io.jaiclaw.core.tenant.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local, in-memory implementation of {@link SessionManager}.
 * Sessions live in a {@link ConcurrentHashMap} and are lost on JVM
 * exit / pod restart. This is the framework default — registered as
 * {@code @ConditionalOnMissingBean SessionManager} so downstream apps
 * can replace it with a durable backend (Redis, Postgres, JCache).
 *
 * <p>Sessions are scoped to the current tenant via {@link TenantGuard}.
 * In MULTI mode, session keys are internally prefixed with tenantId
 * for defense-in-depth.
 *
 * <p>Fires {@link SessionStartedEvent} when a session is first created
 * and {@link SessionEndedEvent} when a session is closed or reset,
 * provided an {@link AgentHookDispatcher} is injected. Hooks fail-safe
 * — if the dispatcher throws, session lifecycle continues.
 */
public class InMemorySessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(InMemorySessionManager.class);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private TenantGuard tenantGuard;
    private AgentHookDispatcher hooks;

    public InMemorySessionManager() {}

    public InMemorySessionManager(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard;
    }

    public InMemorySessionManager(TenantGuard tenantGuard, AgentHookDispatcher hooks) {
        this.tenantGuard = tenantGuard;
        this.hooks = hooks;
    }

    @Override
    public void setTenantGuard(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard;
    }

    @Override
    public void setHookDispatcher(AgentHookDispatcher hooks) {
        this.hooks = hooks;
    }

    private String scopedKey(String sessionKey) {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            String prefix = tenantGuard.resolveTenantPrefix();
            if (!sessionKey.startsWith(prefix + ":")) {
                return prefix + ":" + sessionKey;
            }
        }
        return sessionKey;
    }

    @Override
    public Session getOrCreate(String sessionKey, String agentId) {
        String key = scopedKey(sessionKey);
        boolean[] created = new boolean[]{false};
        Session session = sessions.computeIfAbsent(key, k -> {
            created[0] = true;
            String tenantId = resolveTenantId();
            return Session.create(UUID.randomUUID().toString(), k, agentId, tenantId);
        });
        if (created[0]) {
            fireVoid(SessionStartedEvent.of(agentId, key));
        }
        return session;
    }

    @Override
    public void appendMessage(String sessionKey, Message message) {
        String key = scopedKey(sessionKey);
        sessions.computeIfPresent(key,
                (k, session) -> session.withMessage(message));
    }

    @Override
    public Optional<Session> get(String sessionKey) {
        String key = scopedKey(sessionKey);
        Session session = sessions.get(key);
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

    @Override
    public Session transitionState(String sessionKey, SessionState newState) {
        String key = scopedKey(sessionKey);
        return sessions.computeIfPresent(key,
                (k, session) -> {
                    log.debug("Session {} state: {} -> {}", sessionKey, session.state(), newState);
                    return session.withState(newState);
                });
    }

    @Override
    public Session close(String sessionKey) {
        Session session = transitionState(sessionKey, SessionState.CLOSED);
        if (session != null) {
            fireVoid(SessionEndedEvent.of(session.agentId(), scopedKey(sessionKey), "closed"));
        }
        return session;
    }

    @Override
    public void replaceMessages(String sessionKey, List<Message> newMessages) {
        String key = scopedKey(sessionKey);
        sessions.computeIfPresent(key,
                (k, session) -> session.withMessages(newMessages));
    }

    @Override
    public void reset(String sessionKey) {
        String key = scopedKey(sessionKey);
        Session removed = sessions.remove(key);
        if (removed != null) {
            fireVoid(SessionEndedEvent.of(removed.agentId(), key, "reset"));
        }
    }

    @Override
    public List<Session> listSessions() {
        String currentTenant = resolveTenantId();
        if (currentTenant == null) {
            return List.copyOf(sessions.values());
        }
        return sessions.values().stream()
                .filter(s -> currentTenant.equals(s.tenantId()))
                .toList();
    }

    @Override
    public List<Session> listActiveSessions() {
        return listSessions().stream()
                .filter(s -> s.state() == SessionState.ACTIVE || s.state() == SessionState.IDLE)
                .toList();
    }

    @Override
    public int messageCount(String sessionKey) {
        return get(sessionKey).map(s -> s.messages().size()).orElse(0);
    }

    @Override
    public boolean exists(String sessionKey) {
        return sessions.containsKey(scopedKey(sessionKey));
    }

    @Override
    public int sessionCount() {
        return listSessions().size();
    }

    private String resolveTenantId() {
        return tenantGuard != null ? tenantGuard.requireTenantIfMulti() : null;
    }

    private void fireVoid(io.jaiclaw.core.hook.event.HookEvent event) {
        if (hooks != null) {
            try {
                hooks.fireVoid(event);
            } catch (Exception e) {
                log.warn("Session hook {} failed: {}", event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
