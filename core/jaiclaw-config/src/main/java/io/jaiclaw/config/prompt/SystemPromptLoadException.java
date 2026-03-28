package io.jaiclaw.config.prompt;

/**
 * Thrown when a system prompt cannot be loaded from its configured source.
 */
public class SystemPromptLoadException extends RuntimeException {

    public SystemPromptLoadException(String message) {
        super(message);
    }

    public SystemPromptLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
