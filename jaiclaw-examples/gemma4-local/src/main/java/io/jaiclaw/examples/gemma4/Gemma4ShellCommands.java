package io.jaiclaw.examples.gemma4;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.AgentRuntimeContext;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.core.model.AgentIdentity;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.Message;
import io.jaiclaw.core.model.UserMessage;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.skills.SkillLoader;
import io.jaiclaw.skills.SkillPromptBuilder;
import io.jaiclaw.tools.ToolRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Spring Shell commands for the Gemma 4 local example.
 * Provides chat, session management, and status commands.
 */
@ShellComponent
public class Gemma4ShellCommands {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int MESSAGE_TRUNCATE_LENGTH = 120;

    private final ObjectProvider<AgentRuntime> agentRuntimeProvider;
    private final SessionManager sessionManager;
    private final JaiClawProperties properties;
    private final ToolRegistry toolRegistry;
    private final SkillLoader skillLoader;
    private final SkillPromptBuilder skillPromptBuilder = new SkillPromptBuilder();
    private String currentSessionKey = "default";

    public Gemma4ShellCommands(ObjectProvider<AgentRuntime> agentRuntimeProvider,
                               SessionManager sessionManager,
                               JaiClawProperties properties,
                               ToolRegistry toolRegistry,
                               SkillLoader skillLoader) {
        this.agentRuntimeProvider = agentRuntimeProvider;
        this.sessionManager = sessionManager;
        this.properties = properties;
        this.toolRegistry = toolRegistry;
        this.skillLoader = skillLoader;
    }

    // ── Chat ──────────────────────────────────────────────────────

    @ShellMethod(key = "chat", value = "Send a message to the Gemma 4 agent")
    public String chat(@ShellOption(help = "Your message") String message) {
        AgentRuntime agentRuntime = agentRuntimeProvider.getIfAvailable();
        if (agentRuntime == null) {
            return "No LLM configured. Ensure Ollama is running and gemma4 model is pulled.";
        }

        String agentId = properties.agent().defaultAgent();
        AgentIdentity identity = new AgentIdentity(
                agentId,
                properties.identity().name(),
                properties.identity().description());
        var session = sessionManager.getOrCreate(currentSessionKey, agentId);
        var context = new AgentRuntimeContext(
                agentId, currentSessionKey, session, identity, ToolProfile.MINIMAL, ".");

        try {
            var response = agentRuntime.run(message, context).join();
            return response.content();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ── Session management ────────────────────────────────────────

    @ShellMethod(key = "new-session", value = "Start a new chat session")
    public String newSession() {
        sessionManager.reset(currentSessionKey);
        currentSessionKey = "session-" + System.currentTimeMillis();
        return "New session started: " + currentSessionKey;
    }

    @ShellMethod(key = "sessions", value = "List all chat sessions")
    public String sessions() {
        var allSessions = sessionManager.listSessions();
        if (allSessions.isEmpty()) {
            return "No active sessions.";
        }
        var sb = new StringBuilder();
        sb.append("%-3s %-30s %-10s %-6s %s%n".formatted("", "SESSION KEY", "STATE", "MSGS", "LAST ACTIVE"));
        sb.append("\u2500".repeat(75)).append('\n');
        for (var session : allSessions) {
            String marker = session.sessionKey().equals(currentSessionKey) ? " * " : "   ";
            sb.append("%-3s %-30s %-10s %-6d %s%n".formatted(
                    marker,
                    truncate(session.sessionKey(), 30),
                    session.state().name(),
                    session.messages().size(),
                    TIME_FMT.format(session.lastActiveAt())
            ));
        }
        return sb.toString();
    }

    @ShellMethod(key = "session-history", value = "Show messages in a session")
    public String sessionHistory(
            @ShellOption(defaultValue = "", help = "Session key (defaults to current)") String sessionKey) {
        var key = sessionKey.isBlank() ? currentSessionKey : sessionKey;
        var sessionOpt = sessionManager.get(key);
        if (sessionOpt.isEmpty()) {
            return "Session not found: " + key;
        }
        var session = sessionOpt.get();
        var messages = session.messages();
        if (messages.isEmpty()) {
            return "No messages in session: " + key;
        }
        var sb = new StringBuilder("Session: %s (%d messages)%n%n".formatted(key, messages.size()));
        for (var msg : messages) {
            String role = formatRole(msg);
            String content = truncate(msg.content(), MESSAGE_TRUNCATE_LENGTH);
            sb.append("[%s] %s  %s%n".formatted(role, TIME_FMT.format(msg.timestamp()), content));
        }
        return sb.toString();
    }

    // ── Status ────────────────────────────────────────────────────

    @ShellMethod(key = "status", value = "Show system status")
    public String status() {
        return """
                Gemma 4 Local — Status
                \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                Identity:  %s
                Agent:     %s
                Tools:     %d registered
                Sessions:  %d active
                """.formatted(
                properties.identity().name(),
                properties.agent().defaultAgent(),
                toolRegistry.size(),
                sessionManager.listSessions().size()
        );
    }

    @ShellMethod(key = "tools", value = "List available tools")
    public String tools() {
        var tools = toolRegistry.resolveAll();
        if (tools.isEmpty()) {
            return "No tools registered.";
        }
        var sb = new StringBuilder("Available Tools:\n");
        for (var tool : tools) {
            sb.append("  %-20s %s%n".formatted(tool.definition().name(), tool.definition().description()));
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

    // ── Helpers ───────────────────────────────────────────────────

    private static String formatRole(Message msg) {
        return switch (msg) {
            case UserMessage u -> "USER";
            case AssistantMessage a -> "ASSISTANT";
            default -> msg.getClass().getSimpleName().replace("Message", "").toUpperCase();
        };
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        String oneLine = text.replace('\n', ' ');
        if (oneLine.length() <= maxLen) return oneLine;
        return oneLine.substring(0, maxLen - 3) + "...";
    }
}
