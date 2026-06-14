package io.jaiclaw.agentmind.tendencies.learning;

import io.jaiclaw.core.model.Tendencies;

import java.util.List;
import java.util.Optional;

/**
 * SPI for computing an updated {@link Tendencies} record from a recent
 * transcript window. Called by the dialectic pipeline whenever the
 * {@code TendenciesCadenceGate} fires.
 *
 * <p>Implementations represent distinct learning strategies:
 * <ul>
 *   <li>{@code deterministic} (default) — keyword / regex extraction; no
 *       LLM cost. Constrained trait vocabulary.</li>
 *   <li>{@code local-llm} (opt-in via
 *       {@code jaiclaw.agentmind.tendencies.provider=local-llm}) —
 *       single-pass against the configured Spring AI ChatModel. Produces
 *       a richer trait map; costs LLM tokens.</li>
 *   <li>{@code honcho} (Phase 4 sub-module) — remote
 *       Honcho-compatible API.</li>
 * </ul>
 *
 * <p>Implementations MUST return a {@link Tendencies} whose
 * {@code version()} is strictly greater than {@code current}'s version, or
 * the empty {@link Optional} when no observable change is warranted (the
 * dialectic pipeline will skip the persistence step).
 *
 * <p>Plan §8 task 3.5.
 */
public interface TendenciesLearningProvider {

    /**
     * Returns the provider type identifier (e.g. {@code "deterministic"},
     * {@code "local-llm"}, {@code "honcho"}). Selectable via
     * {@code jaiclaw.agentmind.tendencies.provider}.
     */
    String type();

    /**
     * Run one dialectic pass over the recent transcript window for a user.
     *
     * @param current  the existing Tendencies record (may be a fresh
     *                 zero-version document if this is the user's first pass)
     * @param transcript ordered list of recent message strings — most
     *                   recent last. Implementations may bound the window
     *                   internally; the cadence trigger passes the full
     *                   active window.
     * @return updated Tendencies (with bumped version + stamped
     *         lastDialecticAt) if the pass produced an observable change;
     *         empty otherwise.
     */
    Optional<Tendencies> learn(Tendencies current, List<String> transcript);
}
