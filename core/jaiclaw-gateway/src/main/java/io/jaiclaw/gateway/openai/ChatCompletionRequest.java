package io.jaiclaw.gateway.openai;

import java.util.List;

/**
 * Minimal OpenAI {@code /v1/chat/completions} request.
 *
 * <p>Only the fields JaiClaw can honour are modelled. Sampling parameters
 * ({@code temperature}, {@code top_p}, {@code max_tokens}) are accepted and
 * <strong>ignored</strong>: the agent's model configuration is owned by the
 * deployment, not by the caller. Silently accepting them keeps stock OpenAI
 * clients working; silently applying them would let any caller reconfigure the
 * agent.
 *
 * @param model    selects the JaiClaw agent id, not a provider model name
 * @param messages conversation so far
 * @param stream   whether to stream SSE chunks
 *
 * <p>Phase 5 of the 1.2.0 plan.
 */
public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        Boolean stream
) {
    public ChatCompletionRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public boolean streaming() {
        return Boolean.TRUE.equals(stream);
    }

    /** The last user message — what the agent is actually asked to answer. */
    public String latestUserContent() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if ("user".equalsIgnoreCase(m.role())) return m.content();
        }
        return null;
    }

    /** @param role {@code system | user | assistant} */
    public record Message(String role, String content) {}
}
