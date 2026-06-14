package io.jaiclaw.agentmind.tendencies.honcho;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal Honcho HTTP client SPI. The sub-module ships only this contract
 * — consumers wire their preferred HTTP client (Spring WebClient, OkHttp,
 * Hertz, etc.) against the live Honcho API. Tests use a Mock; the
 * AgentMind demo app uses {@link NoOpHonchoClient} so the demo runs
 * without a live Honcho server.
 *
 * <p>Workspace ↔ tenant, peerName ↔ canonicalUserId mapping is handled
 * by {@link HonchoRemoteTendenciesProvider} — consumers do not need to
 * know about JaiClaw's tenant model.
 *
 * <p>Plan §8 task 4.1 — Honcho sub-module.
 */
public interface HonchoClient {

    /**
     * Run one dialectic pass against the Honcho server for the given
     * workspace + peer. The transcript is the recent message window;
     * Honcho's server-side dialectic produces (a) updated peer-card
     * markdown and (b) a structured trait map.
     *
     * @param workspace    Honcho workspace key (maps to JaiClaw tenantId)
     * @param peerName     Honcho peer name (maps to JaiClaw canonicalUserId)
     * @param transcript   recent user-message window, most recent last
     * @return {@link Optional#empty()} when Honcho declines (no observable
     *         change); otherwise the (markdown, traits) tuple
     */
    Optional<HonchoDialecticResult> dialectic(String workspace, String peerName,
                                               List<String> transcript);

    /**
     * Estimated token cost of a dialectic pass. Used by the
     * {@code TendenciesTokenBudget} to gate calls; the default returns a
     * conservative 2,000 tokens.
     */
    default long estimatedTokensPerPass() { return 2_000L; }

    /**
     * Dialectic result returned by the Honcho server.
     */
    record HonchoDialecticResult(String peerCardMarkdown, Map<String, String> traits) {
        public HonchoDialecticResult {
            if (peerCardMarkdown == null) peerCardMarkdown = "";
            if (traits == null) traits = Map.of();
            else traits = Map.copyOf(traits);
        }
    }
}
