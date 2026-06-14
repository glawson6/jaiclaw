package io.jaiclaw.agentmind.memory.overflow;

import io.jaiclaw.core.agent.MemoryOverflowException;
import io.jaiclaw.core.model.MemoryDocument;

/**
 * SPI consulted by the memory agent tool when a write would push a document
 * past its {@code charBudget}. The default {@link FailFastOverflowPolicy}
 * raises {@link MemoryOverflowException} so the LLM gets a structured tool
 * error and is forced to consolidate in-turn (the upstream hermes
 * error-as-control-flow pattern, analysis §8.1).
 *
 * <p>Plugins can replace this with a truncate/evict/summarise policy when a
 * deployment prefers silent shrinkage over an in-turn LLM consolidation
 * round-trip. Implementations MUST return a document whose content fits
 * within {@code attempted.charBudget()} or raise.
 *
 * <p>Plan §6 task 2.4.
 */
public interface MemoryOverflowPolicy {

    /**
     * Resolve an overflow. {@code attempted} is the document the agent tool
     * intended to write — its content length already exceeds
     * {@code attempted.charBudget()}.
     *
     * @return a replacement document whose content fits within budget
     * @throws MemoryOverflowException if the policy chooses fail-fast
     */
    MemoryDocument resolve(MemoryDocument attempted);
}
