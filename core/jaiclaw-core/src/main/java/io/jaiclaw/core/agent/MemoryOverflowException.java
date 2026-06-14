package io.jaiclaw.core.agent;

import io.jaiclaw.core.model.MemoryScope;

/**
 * Thrown when a {@code memory add} or {@code memory replace} would push a
 * {@link io.jaiclaw.core.model.MemoryDocument}'s content past its
 * {@code charBudget}. The {@code memory} agent tool translates this into a
 * {@code ToolResult.Error} so the LLM gets a structured prompt back and is
 * forced to consolidate in-turn — hermes' "error-as-control-flow" pattern
 * (analysis §8.1).
 */
public class MemoryOverflowException extends RuntimeException {

    private final MemoryScope scope;
    private final int charBudget;
    private final int attemptedLength;

    public MemoryOverflowException(MemoryScope scope, int charBudget, int attemptedLength) {
        super("Memory overflow: " + scope + "-scope write of " + attemptedLength
                + " chars exceeds budget of " + charBudget
                + " chars. Consolidate existing content via 'replace' or 'remove'.");
        this.scope = scope;
        this.charBudget = charBudget;
        this.attemptedLength = attemptedLength;
    }

    public MemoryScope scope() { return scope; }
    public int charBudget() { return charBudget; }
    public int attemptedLength() { return attemptedLength; }
}
