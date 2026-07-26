package io.jaiclaw.session.redis;

import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.hook.event.HookEvent;
import io.jaiclaw.core.hook.event.SessionEndedEvent;
import io.jaiclaw.core.hook.event.SessionStartedEvent;
import io.jaiclaw.core.model.Message;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.model.SessionState;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed {@link SessionManager} for durable chat history that
 * survives pod restarts. Uses {@link StringRedisTemplate} synchronously
 * — matching the pattern of
 * {@code io.jaiclaw.tasks.persistence.redis.RedisTaskStore}.
 *
 * <p>Two keys per session:
 * <pre>
 *   {prefix}:{tenantId}:{sessionKey}          — JSON metadata envelope
 *   {prefix}:{tenantId}:{sessionKey}:msgs     — Redis LIST of JSON message envelopes
 * </pre>
 * Plus a tenant-scoped index for {@code listSessions()}:
 * <pre>
 *   {prefix}:{tenantId}:idx:sessions          — SET of sessionKeys
 * </pre>
 *
 * <p>{@code appendMessage} is O(1) via RPUSH. {@code transitionState}
 * uses {@code WATCH/MULTI/EXEC} for atomic compare-and-set on the
 * metadata blob, retrying up to {@link #TRANSITION_RETRIES} times if
 * another writer beat it. Every write path refreshes TTL on the two
 * per-session keys; the index set has no TTL (small, shared) and is
 * pruned lazily on read.
 *
 * <p>Tenant isolation is defense-in-depth: keys carry the tenant id in
 * their physical path, and {@link #get(String)} additionally re-validates
 * against the decoded {@link Session#tenantId()} field — same belt-
 * and-braces stance as
 * {@code io.jaiclaw.agent.session.InMemorySessionManager}.
 *
 * <p>{@code sessionCount()} may temporarily over-count sessions whose
 * data has expired since the index set is pruned lazily. This is
 * eventually consistent.
 */
public class RedisSessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionManager.class);

    /** Retry budget for WATCH-guarded transitionState updates. */
    private static final int TRANSITION_RETRIES = 3;

    private final StringRedisTemplate template;
    private final String prefix;
    private final Duration ttl;
    private final SessionCodec codec = new SessionCodec();

    private TenantGuard tenantGuard;
    private AgentHookDispatcher hooks;

    public RedisSessionManager(StringRedisTemplate template) {
        this(template, new TenantGuard(TenantProperties.DEFAULT),
                "jaiclaw:sessions", Duration.ofDays(30));
    }

    public RedisSessionManager(StringRedisTemplate template,
                                TenantGuard tenantGuard,
                                String prefix,
                                Duration ttl) {
        if (template == null) throw new IllegalArgumentException("template must not be null");
        this.template = template;
        this.tenantGuard = tenantGuard != null ? tenantGuard
                : new TenantGuard(TenantProperties.DEFAULT);
        this.prefix = (prefix == null || prefix.isBlank()) ? "jaiclaw:sessions" : prefix;
        this.ttl = ttl != null ? ttl : Duration.ofDays(30);
    }

    @Override
    public void setTenantGuard(TenantGuard tenantGuard) {
        if (tenantGuard != null) this.tenantGuard = tenantGuard;
    }

    @Override
    public void setHookDispatcher(AgentHookDispatcher hooks) {
        this.hooks = hooks;
    }

    // ── SessionManager surface ───────────────────────

    @Override
    public Session getOrCreate(String sessionKey, String agentId) {
        String metadataKey = metadataKey(sessionKey);
        String existing = template.opsForValue().get(metadataKey);
        if (existing != null) {
            List<Message> messages = loadMessages(sessionKey);
            return codec.decodeSession(existing, messages);
        }
        String tenantId = resolveTenantId();
        Session fresh = Session.create(UUID.randomUUID().toString(),
                sessionKey, agentId, tenantId);
        String encoded = codec.encodeSession(fresh);
        // SET NX — only if not already present. If the NX loses a race,
        // re-read and return the winning session.
        Boolean created = template.opsForValue().setIfAbsent(metadataKey, encoded, ttl);
        if (Boolean.TRUE.equals(created)) {
            addToIndex(sessionKey);
            fireVoid(SessionStartedEvent.of(agentId, sessionKey));
            return fresh;
        }
        String raced = template.opsForValue().get(metadataKey);
        if (raced == null) {
            // Astonishing — SETNX said "already there" but GET now empty.
            // Rare, but tolerate: return the fresh session anyway.
            return fresh;
        }
        return codec.decodeSession(raced, loadMessages(sessionKey));
    }

    @Override
    public void appendMessage(String sessionKey, Message message) {
        String metadataKey = metadataKey(sessionKey);
        if (Boolean.FALSE.equals(template.hasKey(metadataKey))) {
            // Match InMemorySessionManager.computeIfPresent — no-op when absent.
            return;
        }
        String messagesKey = messagesKey(sessionKey);
        template.opsForList().rightPush(messagesKey, codec.encodeMessage(message));
        template.expire(messagesKey, ttl);
        // Refresh metadata TTL + bump lastActiveAt.
        updateMetadata(sessionKey, session -> session.withMessage(message).withMessages(session.messages()));
    }

    @Override
    public Optional<Session> get(String sessionKey) {
        String metadataKey = metadataKey(sessionKey);
        String encoded = template.opsForValue().get(metadataKey);
        if (encoded == null) return Optional.empty();
        List<Message> messages = loadMessages(sessionKey);
        Session session = codec.decodeSession(encoded, messages);
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
    public void replaceMessages(String sessionKey, List<Message> newMessages) {
        String metadataKey = metadataKey(sessionKey);
        if (Boolean.FALSE.equals(template.hasKey(metadataKey))) return;
        String messagesKey = messagesKey(sessionKey);
        List<String> encoded = new ArrayList<>(newMessages.size());
        for (Message m : newMessages) encoded.add(codec.encodeMessage(m));
        template.execute(new SessionCallback<Object>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> ops) {
                @SuppressWarnings("unchecked")
                RedisOperations<String, String> sops = (RedisOperations<String, String>) ops;
                sops.multi();
                sops.delete(messagesKey);
                if (!encoded.isEmpty()) {
                    sops.opsForList().rightPushAll(messagesKey, encoded);
                }
                sops.expire(messagesKey, ttl);
                return sops.exec();
            }
        });
        updateMetadata(sessionKey, session -> session.withMessages(newMessages));
    }

    @Override
    public Session transitionState(String sessionKey, SessionState newState) {
        String metadataKey = metadataKey(sessionKey);
        for (int attempt = 0; attempt < TRANSITION_RETRIES; attempt++) {
            Session updated = template.execute(new SessionCallback<Session>() {
                @Override
                public <K, V> Session execute(RedisOperations<K, V> ops) {
                    @SuppressWarnings("unchecked")
                    RedisOperations<String, String> sops = (RedisOperations<String, String>) ops;
                    sops.watch(metadataKey);
                    String current = sops.opsForValue().get(metadataKey);
                    if (current == null) {
                        sops.unwatch();
                        return null;
                    }
                    List<Message> messages = loadMessages(sessionKey);
                    Session decoded = codec.decodeSession(current, messages);
                    log.debug("Session {} state: {} -> {}", sessionKey, decoded.state(), newState);
                    Session next = decoded.withState(newState);
                    sops.multi();
                    sops.opsForValue().set(metadataKey, codec.encodeSession(next), ttl);
                    List<Object> results = sops.exec();
                    if (results == null || results.isEmpty()) {
                        // WATCH aborted — retry.
                        return null;
                    }
                    return next;
                }
            });
            if (updated != null) return updated;
        }
        log.warn("transitionState({}, {}) exhausted retries", sessionKey, newState);
        return null;
    }

    @Override
    public Session close(String sessionKey) {
        Session closed = transitionState(sessionKey, SessionState.CLOSED);
        if (closed != null) {
            fireVoid(SessionEndedEvent.of(closed.agentId(), sessionKey, "closed"));
        }
        return closed;
    }

    @Override
    public void reset(String sessionKey) {
        String metadataKey = metadataKey(sessionKey);
        String messagesKey = messagesKey(sessionKey);
        String encoded = template.opsForValue().get(metadataKey);
        Session decoded = encoded == null ? null : codec.decodeSession(encoded, List.of());
        List<Object> results = template.execute(new SessionCallback<List<Object>>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> ops) {
                @SuppressWarnings("unchecked")
                RedisOperations<String, String> sops = (RedisOperations<String, String>) ops;
                sops.multi();
                sops.delete(metadataKey);
                sops.delete(messagesKey);
                sops.opsForSet().remove(indexKey(), sessionKey);
                return sops.exec();
            }
        });
        if (decoded != null && results != null) {
            fireVoid(SessionEndedEvent.of(decoded.agentId(), sessionKey, "reset"));
        }
    }

    @Override
    public List<Session> listSessions() {
        Set<String> members = template.opsForSet().members(indexKey());
        if (members == null || members.isEmpty()) return List.of();
        List<Session> sessions = new ArrayList<>();
        Set<String> stale = new LinkedHashSet<>();
        String currentTenant = resolveTenantId();
        for (String sessionKey : members) {
            String encoded = template.opsForValue().get(metadataKey(sessionKey));
            if (encoded == null) {
                stale.add(sessionKey);
                continue;
            }
            // Advisory: skip loading messages for list-only paths.
            Session decoded = codec.decodeSession(encoded, List.of());
            if (currentTenant == null || decoded.tenantId() == null
                    || currentTenant.equals(decoded.tenantId())) {
                sessions.add(decoded);
            }
        }
        if (!stale.isEmpty()) {
            template.opsForSet().remove(indexKey(), stale.toArray(new Object[0]));
        }
        return sessions;
    }

    @Override
    public List<Session> listActiveSessions() {
        return listSessions().stream()
                .filter(s -> s.state() == SessionState.ACTIVE || s.state() == SessionState.IDLE)
                .toList();
    }

    @Override
    public int messageCount(String sessionKey) {
        Long size = template.opsForList().size(messagesKey(sessionKey));
        return size == null ? 0 : size.intValue();
    }

    @Override
    public boolean exists(String sessionKey) {
        return Boolean.TRUE.equals(template.hasKey(metadataKey(sessionKey)));
    }

    @Override
    public int sessionCount() {
        Long size = template.opsForSet().size(indexKey());
        return size == null ? 0 : size.intValue();
    }

    // ── internals ────────────────────────────────────

    private String metadataKey(String sessionKey) {
        return prefix + ":" + tenantSegment() + ":" + sessionKey;
    }

    private String messagesKey(String sessionKey) {
        return metadataKey(sessionKey) + ":msgs";
    }

    private String indexKey() {
        return prefix + ":" + tenantSegment() + ":idx:sessions";
    }

    /**
     * Returns the tenant id (in MULTI mode) or the empty string (SINGLE),
     * keeping the physical key shape identical across modes so a rollout
     * from SINGLE → MULTI doesn't strand keys.
     */
    private String tenantSegment() {
        if (tenantGuard == null || !tenantGuard.isMultiTenant()) return "";
        String tenant = tenantGuard.resolveTenantPrefix();
        return tenant == null ? "" : tenant;
    }

    private String resolveTenantId() {
        return tenantGuard != null ? tenantGuard.requireTenantIfMulti() : null;
    }

    private void addToIndex(String sessionKey) {
        template.opsForSet().add(indexKey(), sessionKey);
    }

    private List<Message> loadMessages(String sessionKey) {
        String messagesKey = messagesKey(sessionKey);
        ListOperations<String, String> list = template.opsForList();
        List<String> raw = list.range(messagesKey, 0, -1);
        if (raw == null || raw.isEmpty()) return List.of();
        List<Message> out = new ArrayList<>(raw.size());
        for (String payload : raw) {
            try {
                out.add(codec.decodeMessage(payload));
            } catch (Exception e) {
                log.warn("Skipping unreadable Redis message for session {}: {}",
                        sessionKey, e.getMessage());
            }
        }
        return out;
    }

    /**
     * Read-modify-write the metadata blob using the supplied transformer.
     * Used for lastActiveAt bumps and messages-list refresh — anywhere
     * we don't need CAS guarantees. Best-effort: if the metadata key
     * disappears between read and write, no update happens.
     */
    private void updateMetadata(String sessionKey, java.util.function.UnaryOperator<Session> transform) {
        String metadataKey = metadataKey(sessionKey);
        String encoded = template.opsForValue().get(metadataKey);
        if (encoded == null) return;
        Session current = codec.decodeSession(encoded, List.of());
        Session next = transform.apply(current);
        template.opsForValue().set(metadataKey, codec.encodeSession(next), ttl);
    }

    private void fireVoid(HookEvent event) {
        if (hooks != null) {
            try {
                hooks.fireVoid(event);
            } catch (Exception e) {
                log.warn("Session hook {} failed: {}", event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    // Not currently used but exposed for future observability.
    @SuppressWarnings("unused")
    ValueOperations<String, String> valueOps() { return template.opsForValue(); }

    @SuppressWarnings("unused")
    SetOperations<String, String> setOps() { return template.opsForSet(); }
}
