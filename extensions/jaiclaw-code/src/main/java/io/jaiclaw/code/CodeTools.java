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
        return all(false);
    }

    public static List<ToolCallback> all(boolean enforceWorkspaceBoundary) {
        return List.of(
                new FileEditTool(enforceWorkspaceBoundary),
                new GlobTool(enforceWorkspaceBoundary),
                new GrepTool(enforceWorkspaceBoundary)
        );
    }

    public static void registerAll(ToolRegistry registry) {
        registry.registerAll(all());
    }

    public static void registerAll(ToolRegistry registry, boolean enforceWorkspaceBoundary) {
        registry.registerAll(all(enforceWorkspaceBoundary));
    }
}
