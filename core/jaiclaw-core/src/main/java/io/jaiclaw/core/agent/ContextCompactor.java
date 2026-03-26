package io.jaiclaw.core.agent;

import io.jaiclaw.core.model.Message;

import java.util.List;
import java.util.function.Function;

/**
 * SPI for context window compaction. Implementations check whether the
 * conversation history exceeds a token budget and, if so, summarize older
 * messages to reclaim space.
 */
public interface ContextCompactor {

    /**
     * Compact messages if the conversation exceeds the context window budget.
     *
     * @param messages          current conversation messages
     * @param contextWindowSize the model's max context tokens
     * @param llmCall           function to call the LLM for summarization
     * @return compacted message list (may be the same list if no compaction needed)
     */
    List<Message> compactIfNeeded(List<Message> messages, int contextWindowSize,
                                  Function<String, String> llmCall);
}
