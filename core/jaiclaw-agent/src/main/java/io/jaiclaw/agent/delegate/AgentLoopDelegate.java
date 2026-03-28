package io.jaiclaw.agent.delegate;

import io.jaiclaw.config.TenantAgentConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SPI for pluggable alternative agent execution engines. A tenant can opt into
 * a different agent loop (e.g., Embabel GOAP planning) instead of the default
 * iterative LLM+tool loop.
 */
public interface AgentLoopDelegate {

    /**
     * Unique identifier for this delegate (e.g., "embabel", "langchain4j").
     */
    String delegateId();

    /**
     * Check if this delegate should handle the given tenant config.
     */
    boolean canHandle(TenantAgentConfig config);

    /**
     * Process a message using this delegate's agent engine (blocking).
     */
    AgentLoopDelegateResult execute(String userInput, AgentLoopDelegateContext context);

    /**
     * Process with streaming support.
     * Default: falls back to blocking, emitting a single result.
     */
    default Flux<String> executeStream(String userInput, AgentLoopDelegateContext context) {
        return Mono.fromCallable(() -> execute(userInput, context))
                .map(AgentLoopDelegateResult::content)
                .flux();
    }
}
