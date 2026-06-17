package io.jaiclaw.examples.pipelinee2e.embabel;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure-compute Embabel agent used by the {@code embabel-pipe} e2e pipeline.
 * Two {@code @Action} methods chain via the typed blackboard flow
 * {@code String → ParsedTicket → ScoreResult}; the second action carries
 * {@code @AchievesGoal}, so {@code ScoreResult} is the terminal goal whose
 * JSON serialization becomes the pipeline stage's output body.
 *
 * <p>No LLM calls — every action does plain Java work, so the pipeline e2e
 * runs in CI without an {@code ANTHROPIC_API_KEY} (or any LLM provider).
 * That's deliberate: this agent proves the JaiClaw pipeline → Embabel
 * wiring on its own; richer LLM-backed agents live in
 * {@link TicketTriageAgent} (gated on {@code ANTHROPIC_API_KEY}).
 *
 * <p>Heavily logged at INFO level so the e2e log file shows the full
 * GOAP transition trail.
 */
@Agent(description = "Parses a ticket descriptor like 'priority:high size:large' and assigns a numeric score")
public class TicketScoringAgent {

    private static final Logger log = LoggerFactory.getLogger(TicketScoringAgent.class);

    @Action(description = "Parse a 'priority:X size:Y' descriptor into a ParsedTicket record")
    public ParsedTicket parse(String input, OperationContext context) {
        log.info("TicketScoringAgent.parse — input='{}'", input);
        String priority = "medium";
        String size = "medium";
        if (input != null) {
            for (String token : input.trim().split("\\s+")) {
                int colon = token.indexOf(':');
                if (colon <= 0) continue;
                String key = token.substring(0, colon).toLowerCase();
                String value = token.substring(colon + 1).toLowerCase();
                switch (key) {
                    case "priority" -> priority = value;
                    case "size"     -> size = value;
                    default         -> log.debug("TicketScoringAgent.parse — ignoring unknown key '{}'", key);
                }
            }
        }
        ParsedTicket parsed = new ParsedTicket(priority, size);
        log.info("TicketScoringAgent.parse — parsed={}", parsed);
        return parsed;
    }

    @Action(description = "Compute a numeric score from the parsed ticket fields")
    @AchievesGoal(description = "A ticket has been parsed and scored")
    public ScoreResult score(ParsedTicket ticket, OperationContext context) {
        log.info("TicketScoringAgent.score — ticket={}", ticket);
        int priorityScore = switch (ticket.priority()) {
            case "critical" -> 100;
            case "high"     -> 70;
            case "medium"   -> 40;
            case "low"      -> 10;
            default         -> 30;
        };
        int sizeScore = switch (ticket.size()) {
            case "xlarge" -> 40;
            case "large"  -> 30;
            case "medium" -> 20;
            case "small"  -> 10;
            default       -> 15;
        };
        int total = priorityScore + sizeScore;
        String label = "priority=" + ticket.priority() + " size=" + ticket.size()
                + " score=" + total;
        ScoreResult result = new ScoreResult(ticket, total, label);
        log.info("TicketScoringAgent.score — result={}", result);
        return result;
    }
}
