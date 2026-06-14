package io.jaiclaw.agentmind.memory.overflow;

import io.jaiclaw.core.agent.MemoryOverflowException;
import io.jaiclaw.core.model.MemoryDocument;

/**
 * Default {@link MemoryOverflowPolicy} — raises {@link MemoryOverflowException}
 * unconditionally so the memory agent tool surfaces a tool-error result to
 * the LLM. This forces the LLM to consolidate in-turn (the upstream hermes
 * error-as-control-flow pattern, analysis §8.1).
 *
 * <p>This is the default precisely because it is the safety property the
 * pattern relies on. Plugins that opt into truncate / summarise policies
 * lose that safety in exchange for fewer LLM round-trips.
 */
public class FailFastOverflowPolicy implements MemoryOverflowPolicy {

    @Override
    public MemoryDocument resolve(MemoryDocument attempted) {
        throw new MemoryOverflowException(
                attempted.scope(),
                attempted.charBudget(),
                attempted.content().length());
    }
}
