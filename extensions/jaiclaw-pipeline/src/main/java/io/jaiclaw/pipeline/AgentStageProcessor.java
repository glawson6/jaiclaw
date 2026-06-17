package io.jaiclaw.pipeline;

import io.jaiclaw.camel.GatewayServiceAccessor;
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort;
import io.jaiclaw.tools.bridge.embabel.OrchestrationResult;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Stage processor for {@code AGENT}-type pipeline stages.
 *
 * <p>Resolves the system-prompt template (supports {@code {{stages.X.output}}},
 * {@code {{input}}}, and {@code {{pipeline.*}}}) and then dispatches based on
 * {@link StageDefinition#runtime()}:
 *
 * <ul>
 *   <li>{@link StageRuntime#NATIVE} (default): routes through
 *       {@link GatewayServiceAccessor#handleSync(String, String, String, String)} —
 *       the same LLM+tool loop in-message agents use. Tool selection is
 *       LLM-sampled and therefore non-deterministic across runs.</li>
 *   <li>{@link StageRuntime#EMBABEL}: routes through an
 *       {@link AgentOrchestrationPort} (provided by
 *       {@code jaiclaw-embabel-delegate} when on the classpath). The GOAP
 *       planner produces a deterministic action sequence given the same
 *       input + agent definition. {@code PipelineValidator} fails fast at
 *       startup if EMBABEL is requested but the port isn't available.</li>
 * </ul>
 */
public class AgentStageProcessor implements StageProcessor {

    private static final Logger log = LoggerFactory.getLogger(AgentStageProcessor.class);

    /** Embabel's default input binding: {@code "it" -> userInput}. */
    private static final String EMBABEL_INPUT_BINDING = "it";

    /** Default timeout for an EMBABEL stage when {@link StageDefinition#timeout()} is null. */
    private static final Duration DEFAULT_EMBABEL_TIMEOUT = Duration.ofMinutes(5);

    private final GatewayServiceAccessor gateway;
    private final AgentOrchestrationPort orchestrationPort;

    public AgentStageProcessor(GatewayServiceAccessor gateway) {
        this(gateway, null);
    }

    public AgentStageProcessor(GatewayServiceAccessor gateway,
                               AgentOrchestrationPort orchestrationPort) {
        this.gateway = gateway;
        this.orchestrationPort = orchestrationPort;
    }

    @Override
    public void process(Exchange exchange, StageDefinition stage, PipelineContext context) throws Exception {
        String input = exchange.getIn().getBody(String.class);

        // Resolve system prompt template (stage outputs + {{input}} + {{pipeline.*}})
        String prompt = stage.systemPrompt();
        if (prompt != null && !prompt.isEmpty()) {
            prompt = TemplateResolver.resolve(prompt, context);
            input = prompt + "\n\n" + (input != null ? input : "");
        }

        StageRuntime runtime = stage.runtime() != null ? stage.runtime() : StageRuntime.NATIVE;
        switch (runtime) {
            case EMBABEL -> processEmbabel(exchange, stage, input);
            case NATIVE  -> processNative(exchange, stage, context, input);
        }
    }

    private void processNative(Exchange exchange, StageDefinition stage,
                                PipelineContext context, String input) {
        log.debug("Agent stage '{}' invoking agent '{}' on channel '{}' (runtime=NATIVE)",
                stage.name(), stage.agentId(), stage.channelId());
        String response = gateway.handleSync(
                stage.channelId(), stage.agentId(),
                "pipeline-" + context.executionId(), input);
        exchange.getIn().setBody(response);
    }

    private void processEmbabel(Exchange exchange, StageDefinition stage, String input)
            throws Exception {
        if (orchestrationPort == null) {
            // Should have been caught by PipelineValidator at startup. Belt-and-braces
            // check so a misconfigured runtime fails loudly at execution time too.
            throw new IllegalStateException(
                    "Stage '" + stage.name() + "' requested runtime=EMBABEL but no "
                            + "AgentOrchestrationPort bean is available. Add "
                            + "jaiclaw-starter-embabel to the classpath.");
        }
        Duration timeout = stage.timeout() != null ? stage.timeout() : DEFAULT_EMBABEL_TIMEOUT;
        String workflow = stage.embabelWorkflow();
        long startNanos = System.nanoTime();

        log.debug("Agent stage '{}' invoking Embabel workflow '{}' (runtime=EMBABEL, timeout={})",
                stage.name(), workflow, timeout);

        OrchestrationResult result = orchestrationPort
                .execute(workflow, Map.of(EMBABEL_INPUT_BINDING, input))
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("Pipeline stage — stage={} runtime=EMBABEL workflow={} success={} duration={}ms",
                stage.name(), workflow, result.success(), elapsedMs);

        if (!result.success()) {
            throw new RuntimeException("Embabel stage '" + stage.name() + "' failed: " + result.error());
        }
        exchange.getIn().setBody(result.output());
    }
}
