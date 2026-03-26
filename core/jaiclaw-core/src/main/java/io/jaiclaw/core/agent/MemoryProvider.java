package io.jaiclaw.core.agent;

/**
 * SPI for loading workspace memory into the agent's system prompt.
 */
public interface MemoryProvider {

    /**
     * Load memory content for the given workspace directory.
     *
     * @param workspaceDir the agent's workspace directory path
     * @return memory content to inject into the system prompt, or empty string if none
     */
    String loadMemory(String workspaceDir);
}
