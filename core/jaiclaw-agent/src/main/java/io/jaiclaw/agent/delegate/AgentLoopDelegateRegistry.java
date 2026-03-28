package io.jaiclaw.agent.delegate;

import io.jaiclaw.config.TenantAgentConfig;

import java.util.List;
import java.util.Optional;

/**
 * Registry of {@link AgentLoopDelegate} implementations. Resolves the first
 * delegate that can handle a given tenant configuration.
 */
public class AgentLoopDelegateRegistry {

    private final List<AgentLoopDelegate> delegates;

    public AgentLoopDelegateRegistry(List<AgentLoopDelegate> delegates) {
        this.delegates = delegates != null ? delegates : List.of();
    }

    /**
     * Find the first delegate that can handle this tenant config.
     * Returns empty if no delegate is enabled or matches.
     */
    public Optional<AgentLoopDelegate> resolve(TenantAgentConfig config) {
        if (config == null || config.loopDelegate() == null || !config.loopDelegate().enabled()) {
            return Optional.empty();
        }
        return delegates.stream()
                .filter(d -> d.canHandle(config))
                .findFirst();
    }

    public int size() {
        return delegates.size();
    }
}
