package io.jaiclaw.config.prompt;

import io.jaiclaw.config.SystemPromptConfig;

/**
 * Strategy interface for loading system prompts from various sources.
 */
public interface SystemPromptLoader {

    /**
     * @return true if this loader supports the given strategy name
     */
    boolean supports(String strategy);

    /**
     * Load the system prompt content for the given configuration.
     *
     * @param config prompt configuration with strategy, source, and content fields
     * @return the loaded prompt text
     * @throws SystemPromptLoadException if loading fails
     */
    String load(SystemPromptConfig config);
}
