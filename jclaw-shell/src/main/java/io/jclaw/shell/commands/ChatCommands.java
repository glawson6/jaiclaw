package io.jclaw.shell.commands;

import io.jclaw.agent.AgentRuntime;
import io.jclaw.agent.AgentRuntimeContext;
import io.jclaw.agent.session.SessionManager;
import io.jclaw.config.JClawProperties;
import io.jclaw.core.model.AssistantMessage;
import io.jclaw.core.model.Message;
import io.jclaw.core.model.Session;
import io.jclaw.core.model.UserMessage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@ShellComponent
@ConditionalOnBean(AgentRuntime.class)
public class ChatCommands {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int MESSAGE_TRUNCATE_LENGTH = 120;

    private final AgentRuntime agentRuntime;
    private final SessionManager sessionManager;
    private final JClawProperties properties;
    private String currentSessionKey = "default";

    public ChatCommands(AgentRuntime agentRuntime, SessionManager sessionManager,
                        JClawProperties properties) {
        this.agentRuntime = agentRuntime;
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    @ShellMethod(key = "chat", value = "Send a message to the agent")
    public String chat(@ShellOption(help = "Your message") String message) {
        var agentId = properties.agent().defaultAgent();
        var session = sessionManager.getOrCreate(currentSessionKey, agentId);
        var context = new AgentRuntimeContext(agentId, currentSessionKey, session);

        try {
            var response = agentRuntime.run(message, context).join();
            return response.content();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

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
        sb.append("─".repeat(75)).append('\n');
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

    String getCurrentSessionKey() {
        return currentSessionKey;
    }

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
