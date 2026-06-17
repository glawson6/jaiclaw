package io.jaiclaw.examples.pipelinee2e.embabel;

/**
 * Terminal goal type produced by {@link TicketScoringAgent}. Its JSON
 * serialization becomes the pipeline stage's output body and is consumed
 * by the downstream PROCESSOR stage in {@code embabel-pipe}.
 *
 * @param ticket the parsed inputs that fed the score
 * @param score  numeric score derived from priority + size lookup tables
 * @param label  human-readable summary tying the two together
 */
public record ScoreResult(ParsedTicket ticket, int score, String label) {}
