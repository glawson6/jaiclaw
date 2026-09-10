package io.jaiclaw.learning;

import io.jaiclaw.core.api.Experimental;

import java.time.Duration;
import java.util.Locale;

/**
 * How eagerly the reviewer proposes things to learn.
 *
 * <p>Three named points rather than a 0.0–1.0 float. The knob is a <em>policy</em>
 * over a queue a human reads, not a sampling parameter: turning it up does not
 * produce bolder insights, it produces more proposals. Named levels are each a
 * tested configuration with a stated intent, so nobody has to guess what 0.63
 * would mean.
 *
 * <p>Note this is <strong>not</strong> the LLM's sampling temperature, and is
 * deliberately not wired to it. The review call is structured JSON extraction;
 * raising sampling temperature there yields malformed output and invented skill
 * names, not better judgement. Selectivity varies the <em>threshold</em> stated
 * in the prompt while the model keeps sampling conservatively.
 *
 * <p>{@link #BALANCED} reproduces exactly the values that were hard-coded before
 * this enum existed, so an existing deployment sees no behaviour change.
 *
 * @see LearningProperties#selectivity()
 */
@Experimental
public enum LearningSelectivity {

    /**
     * Propose rarely. For deployments where the review queue is read by someone
     * whose time is expensive, or where a wrong learned skill is costly.
     */
    CONSERVATIVE(6, Duration.ofMinutes(15), 2,
            """
            Be highly conservative. The overwhelming majority of conversations teach \
            nothing worth keeping, and proposing nothing is the expected outcome. \
            Propose only what you would bet money will recur and matter again. \
            A queue full of marginal suggestions is worse than an empty one, because \
            a human has to read every entry."""),

    /**
     * The default, and the behaviour of every release before this enum existed:
     * min-turns 4, a 5-minute per-session window, and at most 5 proposals per review.
     */
    BALANCED(4, Duration.ofMinutes(5), 5,
            """
            Be conservative. Most conversations teach nothing worth keeping. Proposing \
            nothing is the correct and common answer — a queue full of marginal \
            suggestions is worse than an empty one, because a human has to read it."""),

    /**
     * Propose freely. For a personal assistant, or an evaluation run where you
     * want to see what the reviewer would surface before tightening the filter.
     *
     * <p>Expect noise. This setting trades a longer queue for better recall, and
     * is a poor fit for {@code mode: auto} in a multi-tenant deployment.
     */
    EAGER(2, Duration.ofMinutes(1), 8,
            """
            Lean towards proposing. Surface anything plausibly reusable, including \
            tentative patterns you are only moderately confident about — a human \
            reviews these before they take effect. Still skip one-off facts and \
            anything already covered by the existing skills and memory below.""");

    private final int minTurns;
    private final Duration minInterval;
    private final int maxProposalsPerReview;
    private final String promptGuidance;

    LearningSelectivity(int minTurns, Duration minInterval,
                        int maxProposalsPerReview, String promptGuidance) {
        this.minTurns = minTurns;
        this.minInterval = minInterval;
        this.maxProposalsPerReview = maxProposalsPerReview;
        this.promptGuidance = promptGuidance;
    }

    /** Minimum session turns before a review is worthwhile. */
    public int minTurns() {
        return minTurns;
    }

    /** Minimum wall-clock gap between reviews of one session. */
    public Duration minInterval() {
        return minInterval;
    }

    /** Cap on proposals accepted from a single review. */
    public int maxProposalsPerReview() {
        return maxProposalsPerReview;
    }

    /** The selectivity paragraph spliced into the reviewer prompt. */
    public String promptGuidance() {
        return promptGuidance;
    }

    /**
     * Parses a configured value, falling back to {@link #BALANCED} for anything
     * unrecognised — a typo in one deployment's YAML should not stop learning,
     * it should behave as it did before the setting existed.
     */
    public static LearningSelectivity parse(String raw) {
        if (raw == null || raw.isBlank()) return BALANCED;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BALANCED;
        }
    }
}
