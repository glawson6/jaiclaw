package io.jaiclaw.pipeline

import io.jaiclaw.camel.GatewayServiceAccessor
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort
import io.jaiclaw.tools.bridge.embabel.OrchestrationResult
import org.apache.camel.Exchange
import org.apache.camel.Message
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Verifies that {@link AgentStageProcessor} dispatches on the new
 * {@code runtime} field — EMBABEL routes through
 * {@link AgentOrchestrationPort}, NATIVE keeps the gateway path.
 *
 * <p>Failure modes (no port wired, port returns failure) bubble as
 * exceptions so Camel's error strategy can route them through the
 * pipeline's configured dead-letter / retry path.
 */
class AgentStageProcessorEmbabelSpec extends Specification {

    GatewayServiceAccessor gateway = Mock()
    AgentOrchestrationPort port = Mock()
    PipelineContext context = new PipelineContext(
            "p1", "exec-1", "tenant-a", "corr-x",
            0, 1, null, null,
            [:], [(PipelineContext.INPUT_METADATA_KEY): "raw input"])

    /** Returns [exchange, message] where the message is wired in both directions. */
    private List<Object> exchangePair(String body) {
        Message msg = Mock()
        msg.getBody(String.class) >> body
        Exchange ex = Mock()
        ex.getIn() >> msg
        return [ex, msg]
    }

    def "EMBABEL stage calls AgentOrchestrationPort with the workflow + 'it' binding and writes output to the exchange"() {
        given:
        AgentStageProcessor processor = new AgentStageProcessor(gateway, port)
        StageDefinition stage = new StageDefinition(
                "classify", StageType.AGENT, null, null, null, null, null,
                Duration.ofSeconds(10), null,
                StageRuntime.EMBABEL, "invoice-classifier")
        def (Exchange ex, Message msg) = exchangePair("invoice body")

        when:
        processor.process(ex, stage, context)

        then:
        1 * port.execute("invoice-classifier", { Map m -> m.get("it") == "invoice body" }) >>
                CompletableFuture.completedFuture(OrchestrationResult.success("classification=invoice"))
        1 * msg.setBody("classification=invoice")
        0 * gateway._
    }

    def "EMBABEL stage prepends resolved systemPrompt before sending to the port"() {
        given:
        AgentStageProcessor processor = new AgentStageProcessor(gateway, port)
        StageDefinition stage = new StageDefinition(
                "extract", StageType.AGENT, null, null,
                "Extract fields from: {{input}}", null, null,
                Duration.ofSeconds(5), null,
                StageRuntime.EMBABEL, "extractor")
        def (Exchange ex, Message _) = exchangePair("hello")

        when:
        processor.process(ex, stage, context)

        then:
        1 * port.execute("extractor", { Map m ->
            String it = m.get("it") as String
            it.contains("Extract fields from: raw input")
            it.contains("hello")
        }) >> CompletableFuture.completedFuture(OrchestrationResult.success("ok"))
    }

    def "EMBABEL stage with no AgentOrchestrationPort wired throws at execution"() {
        given:
        AgentStageProcessor processor = new AgentStageProcessor(gateway, null)
        StageDefinition stage = new StageDefinition(
                "stage-x", StageType.AGENT, null, null, null, null, null,
                null, null,
                StageRuntime.EMBABEL, "missing-workflow")
        def (Exchange ex, Message _) = exchangePair("input")

        when:
        processor.process(ex, stage, context)

        then:
        IllegalStateException e = thrown()
        e.message.contains("runtime=EMBABEL")
        e.message.contains("jaiclaw-starter-embabel")
    }

    def "EMBABEL stage propagates port failure as an exception"() {
        given:
        AgentOrchestrationPort failingPort = Stub() {
            execute(_, _) >> CompletableFuture.completedFuture(
                    OrchestrationResult.failure("workflow blew up"))
        }
        AgentStageProcessor processor = new AgentStageProcessor(gateway, failingPort)
        StageDefinition stage = new StageDefinition(
                "validate", StageType.AGENT, null, null, null, null, null,
                Duration.ofSeconds(2), null,
                StageRuntime.EMBABEL, "validator")
        def (Exchange ex, Message _) = exchangePair("payload")

        when:
        processor.process(ex, stage, context)

        then:
        RuntimeException e = thrown()
        e.message.contains("Embabel stage 'validate' failed")
        e.message.contains("workflow blew up")
    }

    def "NATIVE stage continues to route through GatewayServiceAccessor (no port call)"() {
        given:
        AgentStageProcessor processor = new AgentStageProcessor(gateway, port)
        StageDefinition stage = new StageDefinition(
                "native-stage", StageType.AGENT, null, "default", null, null, null, null, null)
        def (Exchange ex, Message msg) = exchangePair("hi")

        when:
        processor.process(ex, stage, context)

        then:
        1 * gateway.handleSync("pipeline-internal", "default", "pipeline-exec-1", "hi") >> "agent response"
        1 * msg.setBody("agent response")
        0 * port._
    }
}
