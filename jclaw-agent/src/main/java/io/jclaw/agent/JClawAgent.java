package io.jclaw.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import io.jclaw.core.model.AssistantMessage;
import io.jclaw.core.model.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Embabel-based agent that handles conversational interactions.
 * Uses GOAP planning to orchestrate LLM calls and tool execution.
 */
@Agent(description = "JClaw conversational agent — processes user input and generates responses using LLM")
public class JClawAgent {

    private static final Logger log = LoggerFactory.getLogger(JClawAgent.class);

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
