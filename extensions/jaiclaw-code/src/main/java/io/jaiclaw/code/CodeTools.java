package io.jaiclaw.code;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for creating and registering all code tools (file_edit, glob, grep).
 */
public final class CodeTools {

    private CodeTools() {}

    public static List<ToolCallback> all() {
        return List.of(
                new FileEditTool(),
                new GlobTool(),
                new GrepTool()
        );
    }

    public static void registerAll(ToolRegistry registry) {
        registry.registerAll(all());
    }
}
