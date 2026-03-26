package io.jaiclaw.compaction;

import io.jaiclaw.core.agent.ContextCompactor;
import io.jaiclaw.core.model.Message;

import java.util.List;
import java.util.function.Function;

/**
 * Adapts the existing {@link CompactionService} to the {@link ContextCompactor} SPI,
 * allowing the agent runtime to invoke compaction without a direct dependency on jaiclaw-compaction.
 */
public class CompactionServiceAdapter implements ContextCompactor {

    private final CompactionService compactionService;

    public CompactionServiceAdapter(CompactionService compactionService) {
        this.compactionService = compactionService;
    }

    @Override
    public List<Message> compactIfNeeded(List<Message> messages, int contextWindowSize,
                                         Function<String, String> llmCall) {
        return compactionService.applyCompaction(messages, contextWindowSize, llmCall);
    }
}
