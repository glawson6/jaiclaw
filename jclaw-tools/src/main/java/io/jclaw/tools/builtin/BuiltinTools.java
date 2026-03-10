package io.jclaw.tools.builtin;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for creating and registering all built-in tools.
 */
public final class BuiltinTools {

    private BuiltinTools() {}

    public static List<ToolCallback> all() {
        return List.of(
                new FileReadTool(),
                new FileWriteTool(),
                new ShellExecTool(),
                new WebFetchTool(),
                new WebSearchTool()
        );
    }

    public static void registerAll(ToolRegistry registry) {
        registry.registerAll(all());
    }
}
