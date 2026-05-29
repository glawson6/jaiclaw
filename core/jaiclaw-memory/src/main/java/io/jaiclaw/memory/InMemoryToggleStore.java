package io.jaiclaw.memory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link MemoryToggleStore}.
 * Tracks disabled sessions — sessions not in the set are considered enabled.
 */
public class InMemoryToggleStore implements MemoryToggleStore {

    private final Set<String> disabledSessions = ConcurrentHashMap.newKeySet();

    @Override
    public void disable(String sessionKey) {
        disabledSessions.add(sessionKey);
    }

    @Override
    public void enable(String sessionKey) {
        disabledSessions.remove(sessionKey);
    }

    @Override
    public boolean isEnabled(String sessionKey) {
        return !disabledSessions.contains(sessionKey);
    }
}
