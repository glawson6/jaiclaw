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

    /**
     * Bounds on live session state. Defaults to unlimited, which is exactly the
     * pre-1.2.0 behaviour — enabling bounds must be a deliberate operator choice,
     * because silently dropping sessions under a running deployment would be
     * worse than the leak it fixes.
     */
    private volatile SessionRetentionPolicy retention = SessionRetentionPolicy.unlimited();

    /** Injectable so idle eviction is testable without sleeping. */
    private volatile java.time.Clock clock = java.time.Clock.systemUTC();

    public InMemorySessionManager() {}

    public InMemorySessionManager(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard;
    }

    public InMemorySessionManager(TenantGuard tenantGuard, AgentHookDispatcher hooks) {
        this.tenantGuard = tenantGuard;
        this.hooks = hooks;
    }

    /** Applies retention bounds. Null restores unlimited. */
    public void setRetentionPolicy(SessionRetentionPolicy policy) {
        this.retention = policy == null ? SessionRetentionPolicy.unlimited() : policy;
    }

    public SessionRetentionPolicy retentionPolicy() {
        return retention;
    }

    /** Test seam for idle eviction. */
    public void setClock(java.time.Clock clock) {
        this.clock = clock == null ? java.time.Clock.systemUTC() : clock;
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
            Session fresh = Session.create(UUID.randomUUID().toString(), k, agentId, tenantId);
            // Session.create stamps Instant.now(); restamp from the injected clock
            // so idle eviction compares like with like. Without this, a fixed test
            // clock would measure age against wall time and never evict.
            java.time.Instant at = clock.instant();
            return Session.builder()
                    .id(fresh.id())
                    .sessionKey(fresh.sessionKey())
                    .agentId(fresh.agentId())
                    .tenantId(fresh.tenantId())
                    .createdAt(at)
                    .lastActiveAt(at)
                    .state(fresh.state())
                    .messages(fresh.messages())
                    .build();
        });
        if (created[0]) {
            fireVoid(SessionStartedEvent.of(agentId, key));
            // Enforce bounds on creation rather than on a timer: it is the only
            // moment the map grows, and it keeps eviction off the read path.
            enforceRetention(key);
        }
        return session;
    }

    @Override
    public void appendMessage(String sessionKey, Message message) {
        String key = scopedKey(sessionKey);
        sessions.computeIfPresent(key, (k, session) -> {
            Session updated = session.withMessage(message);
            int cap = retention.maxMessagesPerSession();
            if (cap > 0 && updated.messages().size() > cap) {
                // Drop from the front: the oldest turns carry the least useful
                // context for what the agent is doing right now.
                var messages = updated.messages();
                updated = updated.withMessages(
                        List.copyOf(messages.subList(messages.size() - cap, messages.size())));
            }
            return updated;
        });
    }

    /**
     * Evicts sessions that exceed the configured bounds.
     *
     * <p>Idle eviction runs first, then size. Never evicts {@code protectedKey} —
     * the session being created — because evicting the caller's own session would
     * be an immediately visible failure for no benefit.
     *
     * @return how many sessions were evicted
     */
    int enforceRetention(String protectedKey) {
        if (retention.isUnlimited()) return 0;
        int evicted = 0;

        if (retention.hasIdleTimeout()) {
            java.time.Instant cutoff = clock.instant().minus(retention.idleTimeout());
            for (var entry : Map.copyOf(sessions).entrySet()) {
                if (entry.getKey().equals(protectedKey)) continue;
                Session s = entry.getValue();
                if (s.lastActiveAt() != null && s.lastActiveAt().isBefore(cutoff)) {
                    if (evict(entry.getKey(), "idle")) evicted++;
                }
            }
        }

        if (retention.hasSessionLimit()) {
            while (sessions.size() > retention.maxSessions()) {
                String oldest = null;
                java.time.Instant oldestSeen = null;
                for (var entry : Map.copyOf(sessions).entrySet()) {
                    if (entry.getKey().equals(protectedKey)) continue;
                    java.time.Instant active = entry.getValue().lastActiveAt();
                    if (active == null) continue;
                    if (oldestSeen == null || active.isBefore(oldestSeen)) {
                        oldestSeen = active;
                        oldest = entry.getKey();
                    }
                }
                // Nothing evictable left (everything is the protected key, or the
                // map shrank underneath us) — stop rather than spin.
                if (oldest == null) break;
                if (evict(oldest, "capacity")) evicted++;
                else break;
            }
        }

        if (evicted > 0) {
            log.debug("Session retention evicted {} session(s); {} remain", evicted, sessions.size());
        }
        return evicted;
    }

    private boolean evict(String key, String reason) {
        Session removed = sessions.remove(key);
        if (removed == null) return false;
        fireVoid(SessionEndedEvent.of(removed.agentId(), key, reason));
        return true;
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
