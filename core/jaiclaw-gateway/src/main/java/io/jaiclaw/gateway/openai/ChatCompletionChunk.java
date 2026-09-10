package io.jaiclaw.gateway.openai;

import java.util.List;

/**
 * One SSE frame of a streamed {@code chat.completion.chunk}.
 *
 * <p>Phase 5 of the 1.2.0 plan.
 */
public record ChatCompletionChunk(
        String id,
        String object,
        long created,
        String model,
        List<Choice> choices
) {
    public static ChatCompletionChunk content(String id, String model, String delta) {
        return new ChatCompletionChunk(id, "chat.completion.chunk",
                System.currentTimeMillis() / 1000, model,
                List.of(new Choice(0, new Delta(null, delta), null)));
    }

    /** The opening frame, which carries the role and no content. */
    public static ChatCompletionChunk start(String id, String model) {
        return new ChatCompletionChunk(id, "chat.completion.chunk",
                System.currentTimeMillis() / 1000, model,
                List.of(new Choice(0, new Delta("assistant", ""), null)));
    }

    /** The closing frame, which carries a finish reason and no content. */
    public static ChatCompletionChunk stop(String id, String model) {
        return new ChatCompletionChunk(id, "chat.completion.chunk",
                System.currentTimeMillis() / 1000, model,
                List.of(new Choice(0, new Delta(null, null), "stop")));
    }

    public record Choice(int index, Delta delta, String finish_reason) {}

    public record Delta(String role, String content) {}
}
