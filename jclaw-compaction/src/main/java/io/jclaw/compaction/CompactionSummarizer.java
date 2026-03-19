package io.jclaw.compaction;

import io.jclaw.core.model.Message;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Summarizes a chunk of conversation messages using an LLM.
 * The LLM call is abstracted via a {@code Function<String, String>} so this class
 * has no Spring AI dependency — the caller provides the summarization function.
 */
public class CompactionSummarizer {

    static final String SUMMARIZATION_PROMPT = """
            Summarize the following conversation chunk. Preserve ALL:
            - Active tasks and their status
            - Decisions made and constraints agreed upon
            - File paths, URLs, UUIDs, IP addresses, and other identifiers (copy verbatim)
            - User preferences and instructions
            - TODO items and next steps
            Be concise but complete. Do not invent information.

            === CONVERSATION ===
            %s
            === END CONVERSATION ===""";

    private final Function<String, String> llmCall;
    private final IdentifierPreserver identifierPreserver;

    public CompactionSummarizer(Function<String, String> llmCall) {
        this.llmCall = llmCall;
        this.identifierPreserver = new IdentifierPreserver();
    }

    public String summarize(List<Message> messages) {
        String conversationText = messages.stream()
                .map(m -> formatMessage(m))
                .collect(Collectors.joining("\n"));

        String prompt = String.format(SUMMARIZATION_PROMPT, conversationText);
        String summary = llmCall.apply(prompt);

        var missing = identifierPreserver.findMissing(conversationText, summary);
        if (!missing.isEmpty()) {
            summary = summary + "\n\nPreserved identifiers: " + String.join(", ", missing);
        }

        return summary;
    }

    private String formatMessage(Message message) {
        String role = switch (message) {
            case io.jclaw.core.model.UserMessage u -> "User";
            case io.jclaw.core.model.AssistantMessage a -> "Assistant";
            case io.jclaw.core.model.SystemMessage s -> "System";
            case io.jclaw.core.model.ToolResultMessage t -> "ToolResult(" + t.toolName() + ")";
        };
        return role + ": " + message.content();
    }
}
