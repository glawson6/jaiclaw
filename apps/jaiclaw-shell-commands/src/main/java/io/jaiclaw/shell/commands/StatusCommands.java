package io.jaiclaw.shell.commands;

import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.plugin.PluginRegistry;
import io.jaiclaw.skills.SkillLoader;
import io.jaiclaw.skills.SkillPromptBuilder;
import io.jaiclaw.tools.ToolRegistry;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class StatusCommands {

    private final JaiClawProperties properties;
    private final ToolRegistry toolRegistry;
    private final PluginRegistry pluginRegistry;
    private final SessionManager sessionManager;
    private final SkillLoader skillLoader;
    private final SkillPromptBuilder skillPromptBuilder = new SkillPromptBuilder();

    public StatusCommands(JaiClawProperties properties, ToolRegistry toolRegistry,
                          PluginRegistry pluginRegistry, SessionManager sessionManager,
                          SkillLoader skillLoader) {
        this.properties = properties;
        this.toolRegistry = toolRegistry;
        this.pluginRegistry = pluginRegistry;
        this.sessionManager = sessionManager;
        this.skillLoader = skillLoader;
    }

    @ShellMethod(key = "status", value = "Show system status")
    public String status() {
        return """
                JaiClaw Status
                ─────────────────────────
                Identity:  %s
                Agent:     %s
                Tools:     %d registered
                Plugins:   %d loaded
                Sessions:  %d active
                """.formatted(
                properties.identity().name(),
                properties.agent().defaultAgent(),
                toolRegistry.size(),
                pluginRegistry.plugins().size(),
                sessionManager.listSessions().size()
        );
    }

    @ShellMethod(key = "tools", value = "List available tools")
    public String tools() {
        var tools = toolRegistry.resolveAll();
        if (tools.isEmpty()) {
            return "No tools registered yet.";
        }
        var sb = new StringBuilder("Available Tools:\n");
        for (var tool : tools) {
            sb.append("  %-20s %s%n".formatted(tool.definition().name(), tool.definition().description()));
        }
        return sb.toString();
    }

    @ShellMethod(key = "plugins", value = "List loaded plugins")
    public String plugins() {
        var plugins = pluginRegistry.plugins();
        if (plugins.isEmpty()) {
            return "No plugins loaded.";
        }
        var sb = new StringBuilder("Loaded Plugins:\n");
        for (var plugin : plugins) {
            sb.append("  %-20s %s [%s]%n".formatted(plugin.id(), plugin.name(), plugin.status()));
        }
        return sb.toString();
    }

    @ShellMethod(key = "skills", value = "List loaded skills")
    public String skills() {
        var skills = skillLoader.loadBundled();
        if (skills.isEmpty()) {
            return "No skills loaded.";
        }
        var sb = new StringBuilder("Loaded Skills:\n");
        sb.append(skillPromptBuilder.buildSkillSummary(skills));
        return sb.toString();
    }

    @ShellMethod(key = "config", value = "Show current JaiClaw configuration")
    public String config() {
        var identity = properties.identity();
        var agent = properties.agent();
        var tools = properties.tools();
        var memory = properties.memory();
        var skillsConfig = properties.skills();

        return """
                JaiClaw Configuration
                ─────────────────────────
                Identity:        %s
                Default Agent:   %s
                Tool Profile:    %s
                Memory Backend:  %s
                Memory Provider: %s
                Memory Model:    %s
                Skills Bundled:  %s
                Skills Watch:    %s
                Plugins:         %s
                """.formatted(
                identity.name(),
                agent.defaultAgent(),
                tools.profile(),
                memory.backend(),
                memory.provider(),
                memory.model() != null ? memory.model() : "(default)",
                String.join(", ", skillsConfig.allowBundled()),
                skillsConfig.watchWorkspace(),
                properties.plugins().enabled()
        );
    }

    @ShellMethod(key = "models", value = "Show configured LLM providers")
    public String models() {
        var sb = new StringBuilder("LLM Providers:\n");
        sb.append("─────────────────────────\n");

        sb.append("  %-15s %s%n".formatted("OpenAI",
                envConfigured("OPENAI_API_KEY") ? "configured" : "not configured"));
        sb.append("  %-15s %s%n".formatted("Anthropic",
                envConfigured("ANTHROPIC_API_KEY") ? "configured" : "not configured"));
        sb.append("  %-15s %s%n".formatted("Gemini",
                envConfigured("GEMINI_API_KEY") ? "configured" : "not configured"));
        sb.append("  %-15s %s%n".formatted("AWS Bedrock",
                envConfigured("AWS_ACCESS_KEY_ID") || envConfigured("AWS_REGION") ? "configured" : "not configured"));
        sb.append("  %-15s %s%n".formatted("Ollama", "configured (localhost:11434)"));

        return sb.toString();
    }

    private static boolean envConfigured(String envVar) {
        String value = System.getenv(envVar);
        return value != null && !value.isBlank();
    }
}
