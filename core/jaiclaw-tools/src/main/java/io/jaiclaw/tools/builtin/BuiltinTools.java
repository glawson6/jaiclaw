package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.exec.ExecPolicyConfig;

import java.util.List;

/**
 * Factory for creating and registering all built-in tools.
 */
public final class BuiltinTools {

    private BuiltinTools() {}

    public static List<ToolCallback> all() {
        return all(ExecPolicyConfig.DEFAULT, false);
    }

    public static List<ToolCallback> all(ExecPolicyConfig execPolicyConfig) {
        return all(execPolicyConfig, false);
    }

    public static List<ToolCallback> all(ExecPolicyConfig execPolicyConfig, boolean ssrfProtection) {
        return List.of(
                new FileReadTool(),
                new FileWriteTool(),
                new ShellExecTool(execPolicyConfig),
                new WebFetchTool(ssrfProtection),
                new WebSearchTool(),
                new ClaudeCliTool()
        );
    }

    public static void registerAll(ToolRegistry registry) {
        registry.registerAll(all());
    }

    public static void registerAll(ToolRegistry registry, ExecPolicyConfig execPolicyConfig) {
        registry.registerAll(all(execPolicyConfig));
    }

    public static void registerAll(ToolRegistry registry, ExecPolicyConfig execPolicyConfig,
                                    boolean ssrfProtection) {
        registry.registerAll(all(execPolicyConfig, ssrfProtection));
    }
}
