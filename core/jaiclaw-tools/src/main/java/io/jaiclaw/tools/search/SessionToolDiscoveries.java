package io.jaiclaw.tools.search;

import io.jaiclaw.core.api.Experimental;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which deferred tools each session has discovered via {@code tool_search}.
 *
 * <p>Once a session has surfaced a tool, that tool's schema is sent on every
 * subsequent turn of the session. Keeping discoveries for the life of the session
 * — rather than for a single turn — is deliberate: a model that discovers
 * {@code file_read}, uses it, and then finds it gone next turn would search for
 * it again every turn, which is worse than never deferring it.
 *
 * <p>The plan called for storing this in {@code Session} attributes so a
 * Redis-backed {@code SessionManager} would persist it. {@code Session} has no
 * attribute map, and adding one to that record for this feature would be a wide
 * change to a core type. This in-memory store is the smaller move; discoveries
 * are cheap to rebuild (one search) and a process restart simply costs one extra
 * {@code tool_search} call. A persistent implementation can replace this class
 * without changing the tool surface.
 *
 * <p>Bounded per session and in total, so a long-lived deployment cannot leak
 * memory through sessions that are never closed.
 *
 * <p>Phase 3 of the 1.2.0 plan.
 */
@Experimental
public class SessionToolDiscoveries {

    /** Maximum discovered tools remembered per session. */
    public static final int MAX_PER_SESSION = 64;

    /** Maximum sessions tracked before the oldest is evicted. */
    public static final int MAX_SESSIONS = 1_000;

    private final int maxPerSession;

    /**
     * Access-ordered LRU: the eldest entry is evicted once {@link #MAX_SESSIONS}
     * is exceeded. Synchronized because agent runs are concurrent.
     */
    private final Map<String, Set<String>> bySession;

    public SessionToolDiscoveries() {
        this(MAX_PER_SESSION, MAX_SESSIONS);
    }

    public SessionToolDiscoveries(int maxPerSession, int maxSessions) {
        this.maxPerSession = Math.max(1, maxPerSession);
        int cap = Math.max(1, maxSessions);
        this.bySession = Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Set<String>> eldest) {
                        return size() > cap;
                    }
                });
    }

    /**
     * Records that a session has discovered these tools.
     *
     * @return the names actually added (excluding those already known)
     */
    public Set<String> discover(String sessionKey, Set<String> toolNames) {
        if (sessionKey == null || toolNames == null || toolNames.isEmpty()) return Set.of();
        Set<String> added = ConcurrentHashMap.newKeySet();
        synchronized (bySession) {
            Set<String> known = bySession.computeIfAbsent(sessionKey, k -> ConcurrentHashMap.newKeySet());
            for (String name : toolNames) {
                if (name == null || name.isBlank()) continue;
                if (known.size() >= maxPerSession && !known.contains(name)) continue;
                if (known.add(name)) added.add(name);
            }
        }
        return Set.copyOf(added);
    }

    /** Tools this session has discovered; empty for an unknown session. */
    public Set<String> discovered(String sessionKey) {
        if (sessionKey == null) return Set.of();
        synchronized (bySession) {
            Set<String> known = bySession.get(sessionKey);
            return known == null ? Set.of() : Set.copyOf(known);
        }
    }

    /** True when this session has surfaced the named tool. */
    public boolean hasDiscovered(String sessionKey, String toolName) {
        if (sessionKey == null || toolName == null) return false;
        synchronized (bySession) {
            Set<String> known = bySession.get(sessionKey);
            return known != null && known.contains(toolName);
        }
    }

    /** Forgets a session's discoveries; called when a session closes or resets. */
    public void clear(String sessionKey) {
        if (sessionKey == null) return;
        bySession.remove(sessionKey);
    }

    /** Forgets everything. */
    public void clearAll() {
        bySession.clear();
    }

    /** Number of sessions currently tracked. */
    public int trackedSessions() {
        synchronized (bySession) {
            return bySession.size();
        }
    }
}
