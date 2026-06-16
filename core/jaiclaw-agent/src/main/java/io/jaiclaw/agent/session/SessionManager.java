package io.jaiclaw.agent.session;

import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.hook.event.SessionEndedEvent;
import io.jaiclaw.core.hook.event.SessionStartedEvent;
import io.jaiclaw.core.model.Message;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.model.SessionState;
import io.jaiclaw.core.tenant.TenantGuard;

import java.util.List;
import java.util.Optional;

/**
 * SPI for chat-session lifecycle and message history. Implementations
 * back the agent runtime's per-session conversation state; the default
 * is {@link InMemorySessionManager} (process-local, lost on restart).
 * Downstream apps that need durable storage (Redis, Postgres, JCache,
 * encrypted-at-rest) provide their own {@code @Bean SessionManager} and
 * the framework's default steps aside via {@code @ConditionalOnMissingBean}.
 *
 * <p>Sessions are scoped to the current tenant via {@link TenantGuard}.
 * In MULTI mode, implementations are expected to prefix session keys
 * with the resolved tenant id for defense-in-depth.
 *
 * <p>Implementations should fire {@link SessionStartedEvent} when a
 * session is first created and {@link SessionEndedEvent} when a session
 * is closed or reset, provided an {@link AgentHookDispatcher} is
 * injected. Hook firing must fail-safe — if the dispatcher throws,
 * session lifecycle must continue.
 */
public interface SessionManager {

    /**
     * Return the existing session for {@code sessionKey}, or create a
     * new one bound to {@code agentId} if none exists. Implementations
     * fire {@link SessionStartedEvent} on first creation.
     */
    Session getOrCreate(String sessionKey, String agentId);

    /**
     * Append a single message to the named session. No-op if the
     * session does not exist.
     */
    void appendMessage(String sessionKey, Message message);

    /**
     * Look up a session by key. Returns empty if the session does not
     * exist or belongs to a different tenant than the current context.
     */
    Optional<Session> get(String sessionKey);

    /**
     * Replace the entire message history of a session. Used by the
     * context compactor to swap raw history for a summarized version.
     * No-op if the session does not exist.
     */
    void replaceMessages(String sessionKey, List<Message> newMessages);

    /**
     * Move a session to a new state. Returns the updated session, or
     * {@code null} if no session exists for the key.
     */
    Session transitionState(String sessionKey, SessionState newState);

    /**
     * Close a session — transitions state to {@link SessionState#CLOSED}
     * and fires {@link SessionEndedEvent} with reason {@code "closed"}.
     * Returns the closed session, or {@code null} if it did not exist.
     */
    Session close(String sessionKey);

    /**
     * Remove a session entirely. Fires {@link SessionEndedEvent} with
     * reason {@code "reset"}. No-op if the session does not exist.
     */
    void reset(String sessionKey);

    /**
     * List all sessions visible to the current tenant. If no tenant
     * context is active, implementations may return every session
     * (backward-compatible single-tenant behavior).
     */
    List<Session> listSessions();

    /**
     * List sessions that are currently {@link SessionState#ACTIVE} or
     * {@link SessionState#IDLE}, scoped to the current tenant.
     */
    List<Session> listActiveSessions();

    /**
     * Number of messages in the named session, or {@code 0} if the
     * session does not exist.
     */
    int messageCount(String sessionKey);

    /**
     * {@code true} if a session exists for the given key (in the
     * current tenant scope).
     */
    boolean exists(String sessionKey);

    /**
     * Total number of sessions visible to the current tenant.
     */
    int sessionCount();

    /**
     * Inject (or replace) the {@link TenantGuard} used for session-key
     * scoping. Called by the auto-config after construction; custom
     * implementations may treat this as optional / no-op if the guard
     * is supplied via constructor.
     */
    void setTenantGuard(TenantGuard tenantGuard);

    /**
     * Inject (or replace) the {@link AgentHookDispatcher} used for
     * firing {@link SessionStartedEvent} / {@link SessionEndedEvent}.
     * Called by the auto-config post-construction to break the wiring
     * cycle between {@code SessionManager} and the hook dispatcher.
     * Custom implementations that do not fire hooks may treat this as
     * a no-op.
     */
    void setHookDispatcher(AgentHookDispatcher hooks);
}
