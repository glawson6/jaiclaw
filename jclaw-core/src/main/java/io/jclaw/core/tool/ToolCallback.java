package io.jclaw.core.tool;

import java.util.Map;

/**
 * SPI for implementing tools. Plugin and built-in tools implement this interface.
 * The {@link io.jclaw.tools.bridge.SpringAiToolBridge} adapts these to Spring AI's ToolCallback.
 */
public interface ToolCallback {

    /** Tool metadata sent to the LLM. */
    ToolDefinition definition();

    /** Execute the tool with the given parameters and runtime context. */
    ToolResult execute(Map<String, Object> parameters, ToolContext context);
}
