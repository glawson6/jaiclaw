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
        return all(ExecPolicyConfig.DEFAULT, true);
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
                new ClaudeCliTool(),
                new AsciiRenderTool(),
                new AsciiBoxTool()
        );
    }

    /**
     * Same as {@link #all(ExecPolicyConfig, boolean)} but omits the default
     * {@link WebSearchTool}.
     *
     * <p>Used by {@code JaiClawToolsAutoConfiguration} so the default
     * {@code web_search} can be supplied as a separate
     * {@code @ConditionalOnMissingBean(name = "webSearchTool")} @Bean —
     * which lets {@code jaiclaw-web-search}'s {@code RegistryWebSearchTool}
     * override it cleanly without colliding inside {@link ToolRegistry}.
     *
     * <p>{@link #all(ExecPolicyConfig, boolean)} itself is intentionally
     * left unchanged so callers like {@code ProjectScanner} (the prompt
     * analyzer's token-budget tool) continue to see the full built-in set.
     */
    public static List<ToolCallback> allExceptWebSearch(ExecPolicyConfig execPolicyConfig,
                                                        boolean ssrfProtection) {
        return List.of(
                new FileReadTool(),
                new FileWriteTool(),
                new ShellExecTool(execPolicyConfig),
                new WebFetchTool(ssrfProtection),
                new ClaudeCliTool(),
                new AsciiRenderTool(),
                new AsciiBoxTool()
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
