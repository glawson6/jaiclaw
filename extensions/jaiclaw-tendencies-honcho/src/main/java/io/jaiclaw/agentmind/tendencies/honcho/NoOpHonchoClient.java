package io.jaiclaw.agentmind.tendencies.honcho;

import java.util.List;
import java.util.Optional;

/**
 * No-op {@link HonchoClient}. Always returns empty — useful for the
 * AgentMind demo app and CI scenarios where a live Honcho server is not
 * available. Production deployments should ship their own HTTP-backed
 * implementation.
 */
public class NoOpHonchoClient implements HonchoClient {
    @Override
    public Optional<HonchoDialecticResult> dialectic(String workspace, String peerName,
                                                      List<String> transcript) {
        return Optional.empty();
    }

    @Override
    public long estimatedTokensPerPass() { return 0L; }
}
