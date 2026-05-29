package io.jaiclaw.memory;

/**
 * SPI for per-session memory enable/disable toggles.
 */
public interface MemoryToggleStore {

    void disable(String sessionKey);

    void enable(String sessionKey);

    boolean isEnabled(String sessionKey);
}
