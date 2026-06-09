package io.jaiclaw.pipeline;

import io.jaiclaw.camel.GatewayServiceAccessor;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage processor that delegates to a JaiClaw agent via {@link GatewayServiceAccessor}.
 * Resolves system prompt templates using {@link TemplateResolver} before sending to the agent.
 */
public class AgentStageProcessor implements StageProcessor {

    private static final Logger log = LoggerFactory.getLogger(AgentStageProcessor.class);

    private final GatewayServiceAccessor gateway;

    public AgentStageProcessor(GatewayServiceAccessor gateway) {
        this.gateway = gateway;
    }

    @Override
    public void process(Exchange exchange, StageDefinition stage, PipelineContext context) throws Exception {
        String input = exchange.getIn().getBody(String.class);

        // Resolve system prompt template with stage output placeholders
        String prompt = stage.systemPrompt();
        if (prompt != null && !prompt.isEmpty()) {
            prompt = TemplateResolver.resolve(prompt, context.stageOutputs());
            // Prepend system prompt to the input
            input = prompt + "\n\n" + (input != null ? input : "");
        }

        String channelId = stage.channelId();
        String agentId = stage.agentId();

        log.debug("Agent stage '{}' invoking agent '{}' on channel '{}'",
                stage.name(), agentId, channelId);

        String response = gateway.handleSync(channelId, agentId, "pipeline-" + context.executionId(), input);
        exchange.getIn().setBody(response);
    }
}
