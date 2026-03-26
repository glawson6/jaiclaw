package io.jaiclaw.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Embabel-based agent that handles conversational interactions.
 * Uses GOAP planning to orchestrate LLM calls and tool execution.
 */
@Agent(description = "JaiClaw conversational agent — processes user input and generates responses using LLM")
public class JaiClawAgent {

    private static final Logger log = LoggerFactory.getLogger(JaiClawAgent.class);

    /**
     * Process user input and generate a response using the LLM.
     * The Embabel framework manages tool calling, blackboard state, and planning.
     */
    @Action
    public AssistantMessage respond(UserMessage userMessage, OperationContext context) {
        log.debug("Processing user message: {}", userMessage.id());

        String response = context.ai()
                .withDefaultLlm()
                .generateText(userMessage.content());

        return new AssistantMessage(
                UUID.randomUUID().toString(),
                response,
                "default"
        );
    }
}
