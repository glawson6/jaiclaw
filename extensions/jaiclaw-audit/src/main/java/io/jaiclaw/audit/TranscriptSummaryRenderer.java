package io.jaiclaw.audit;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a {@link TranscriptSession} as a Markdown summary.
 */
public class TranscriptSummaryRenderer {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final int MAX_CONTENT_LENGTH = 1000;

    /**
     * Render a transcript session as Markdown text.
     */
    public String render(TranscriptSession session) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Transcript: ").append(session.sessionId()).append("\n\n");
        sb.append("- **Agent:** ").append(session.agentId() != null ? session.agentId() : "default").append("\n");
        sb.append("- **Channel:** ").append(session.channel() != null ? session.channel() : "unknown").append("\n");
        sb.append("- **Started:** ").append(TIME_FMT.format(session.startTime())).append(" UTC\n");
        sb.append("- **Messages:** ").append(session.utterances().size()).append("\n\n");
        sb.append("---\n\n");

        List<TranscriptUtterance> utterances = session.utterances();
        for (int i = 0; i < utterances.size(); i++) {
            TranscriptUtterance utterance = utterances.get(i);
            String roleLabel = formatRole(utterance.role());
            sb.append("### ").append(roleLabel);

            if (utterance.metadata().containsKey("toolName")) {
                sb.append(" (").append(utterance.metadata().get("toolName")).append(")");
            }

            sb.append("\n");
            sb.append("*").append(TIME_FMT.format(utterance.timestamp())).append(" UTC*\n\n");

            String content = utterance.content();
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + "\n\n*... (truncated)*";
            }
            sb.append(content).append("\n\n");
        }

        return sb.toString();
    }

    private String formatRole(String role) {
        return switch (role) {
            case "user" -> "User";
            case "assistant" -> "Assistant";
            case "system" -> "System";
            case "tool" -> "Tool Result";
            default -> role;
        };
    }
}
