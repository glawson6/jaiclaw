package io.jaiclaw.examples.pipelinee2e.embabel;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.LlmOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM-backed Embabel agent for the {@code embabel-triage-pipe} pipeline.
 * Registered as a Spring bean only when {@code ANTHROPIC_API_KEY} is set
 * (see {@code EmbabelAgentsConfiguration}), so the default e2e run that
 * exercises {@link TicketScoringAgent} stays LLM-free.
 *
 * <p>Single {@code @Action} marked {@code @AchievesGoal} — given a
 * free-form ticket description as the blackboard input, calls the
 * configured chat model via {@code context.ai().createObject(...)} to
 * produce a structured {@link TriageRecommendation}.
 */
@Agent(description = "Triages a free-form ticket description into a category + severity + recommended team")
public class TicketTriageAgent {

    private static final Logger log = LoggerFactory.getLogger(TicketTriageAgent.class);

    private static LlmOptions defaultLlmOptions() {
        LlmOptions opts = new LlmOptions(DefaultModelSelectionCriteria.INSTANCE);
        opts.setMaxTokens(2048);
        return opts;
    }

    @Action(description = "Classify the ticket and recommend a triage outcome")
    @AchievesGoal(description = "A ticket has been triaged with category, severity, team, and rationale")
    public TriageRecommendation triage(String ticketDescription, OperationContext context) {
        log.info("TicketTriageAgent.triage — input length={} chars", ticketDescription.length());
        TriageRecommendation rec = context.ai()
                .withLlm(defaultLlmOptions())
                .createObject(
                        """
                        Triage this support ticket. Respond with ONLY a JSON object — no other text.

                        Example shape:
                        {"category":"bug","severity":"high","suggestedTeam":"platform","rationale":"..."}

                        Ticket:
                        """ + ticketDescription,
                        TriageRecommendation.class);
        log.info("TicketTriageAgent.triage — recommendation={}", rec);
        return rec;
    }
}
