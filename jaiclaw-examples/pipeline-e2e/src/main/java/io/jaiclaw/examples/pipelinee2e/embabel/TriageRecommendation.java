package io.jaiclaw.examples.pipelinee2e.embabel;

/**
 * Goal type produced by {@link TicketTriageAgent}. Surfaced as JSON to the
 * downstream pipeline stage.
 *
 * @param category   one of "bug", "feature-request", "support", "other"
 * @param severity   one of "low", "medium", "high", "critical"
 * @param suggestedTeam the team that should pick this up
 * @param rationale  one-sentence explanation for the categorization
 */
public record TriageRecommendation(
        String category,
        String severity,
        String suggestedTeam,
        String rationale
) {}
