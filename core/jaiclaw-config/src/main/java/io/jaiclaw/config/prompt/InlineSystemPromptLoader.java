package io.jaiclaw.config.prompt;

import io.jaiclaw.config.SystemPromptConfig;

/**
 * Returns the inline prompt content directly from {@link SystemPromptConfig#content()}.
 */
public class InlineSystemPromptLoader implements SystemPromptLoader {

    @Override
    public boolean supports(String strategy) {
        return "inline".equalsIgnoreCase(strategy);
    }

    @Override
    public String load(SystemPromptConfig config) {
        return config.content() != null ? config.content() : "";
    }
}
