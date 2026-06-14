package io.jaiclaw.agentmind.tendencies.honcho;

import io.jaiclaw.agentmind.tendencies.learning.TendenciesLearningProvider;
import io.jaiclaw.core.model.Tendencies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Remote Honcho-compatible {@link TendenciesLearningProvider}. Delegates
 * the dialectic pass to a {@link HonchoClient}, mapping
 * (tenantId, canonicalUserId) → (workspace, peerName).
 *
 * <p>Activated only when:
 * <ul>
 *   <li>{@code jaiclaw.agentmind.tendencies.provider=honcho}</li>
 *   <li>A {@code HonchoClient} bean is on the classpath (consumers wire
 *       their own HTTP implementation; the demo uses
 *       {@link NoOpHonchoClient}).</li>
 * </ul>
 *
 * <p>Plan §8 task 4.2 — Honcho provider.
 */
public class HonchoRemoteTendenciesProvider implements TendenciesLearningProvider {

    public static final String TYPE = "honcho";

    private static final Logger log = LoggerFactory.getLogger(HonchoRemoteTendenciesProvider.class);

    private final HonchoClient client;

    public HonchoRemoteTendenciesProvider(HonchoClient client) {
        this.client = client;
    }

    @Override
    public String type() { return TYPE; }

    @Override
    public Optional<Tendencies> learn(Tendencies current, List<String> transcript) {
        if (transcript == null || transcript.isEmpty()) return Optional.empty();
        try {
            Optional<HonchoClient.HonchoDialecticResult> result = client.dialectic(
                    current.tenantId(), current.canonicalUserId(), transcript);
            if (result.isEmpty()) return Optional.empty();
            HonchoClient.HonchoDialecticResult r = result.get();
            return Optional.of(current.withDialecticResult(r.peerCardMarkdown(), r.traits()));
        } catch (RuntimeException e) {
            log.warn("Honcho dialectic call failed for {}:{} — skipping pass: {}",
                    current.tenantId(), current.canonicalUserId(), e.getMessage());
            return Optional.empty();
        }
    }
}
