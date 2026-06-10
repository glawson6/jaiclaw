package io.jaiclaw.core.tool;

import io.jaiclaw.core.api.Stable;

import java.util.Map;

/**
 * Result of executing a tool — either success with content or an error.
 *
 * <p>0.8.0 P3.5: {@link Stable}.
 */
@Stable
public sealed interface ToolResult permits ToolResult.Success, ToolResult.Error {

    record Success(String content, Map<String, Object> metadata) implements ToolResult {
        public Success(String content) {
            this(content, Map.of());
        }
    }

    record Error(String message, Throwable cause) implements ToolResult {
        public Error(String message) {
            this(message, null);
        }
    }
}
