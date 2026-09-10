package io.jaiclaw.gateway.openai;

import java.util.List;

/**
 * Minimal OpenAI {@code chat.completion} response.
 *
 * <p>Phase 5 of the 1.2.0 plan.
 */
public record ChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices,
        Usage usage
) {
    public static ChatCompletionResponse of(String id, String model, String content,
                                            int promptTokens, int completionTokens) {
        return new ChatCompletionResponse(
                id, "chat.completion", System.currentTimeMillis() / 1000, model,
                List.of(new Choice(0, new ChatCompletionRequest.Message("assistant", content), "stop")),
                new Usage(promptTokens, completionTokens, promptTokens + completionTokens));
    }

    public record Choice(int index, ChatCompletionRequest.Message message, String finish_reason) {}

    public record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}
}
