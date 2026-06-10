package io.jaiclaw.core.tool;

import io.jaiclaw.core.api.Stable;

import java.util.Map;

/**
 * SPI for implementing tools. Plugin and built-in tools implement this interface.
 * The {@link io.jaiclaw.tools.bridge.SpringAiToolBridge} adapts these to Spring AI's ToolCallback.
 *
 * <p>0.8.0 P3.5: {@link Stable} — committed to no breaking changes
 * across minor releases after 1.0.
 */
@Stable
public interface ToolCallback {

    /** Tool metadata sent to the LLM. */
    ToolDefinition definition();

    /** Execute the tool with the given parameters and runtime context. */
    ToolResult execute(Map<String, Object> parameters, ToolContext context);
}
