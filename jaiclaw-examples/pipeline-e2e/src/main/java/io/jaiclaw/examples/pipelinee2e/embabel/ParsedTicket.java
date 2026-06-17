package io.jaiclaw.examples.pipelinee2e.embabel;

/**
 * Intermediate blackboard type for {@link TicketScoringAgent}. The first
 * {@code @Action} parses an input descriptor like {@code "priority:high size:large"}
 * into this typed record; the second {@code @Action} consumes it.
 *
 * @param priority "low" | "medium" | "high" | "critical"
 * @param size     "small" | "medium" | "large" | "xlarge"
 */
public record ParsedTicket(String priority, String size) {
    public ParsedTicket {
        if (priority == null || priority.isBlank()) priority = "medium";
        if (size == null || size.isBlank()) size = "medium";
    }
}
