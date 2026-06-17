package io.jaiclaw.pipeline.validation

import io.jaiclaw.channel.ChannelRegistry
import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineProperties
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageRuntime
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort
import io.jaiclaw.tools.bridge.embabel.WorkflowDescriptor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationContext
import spock.lang.Specification

/**
 * Validator coverage for the new {@code runtime: EMBABEL} stage path.
 *
 * <p>Verifies that an EMBABEL-runtime stage fails fast at startup when
 * the {@link AgentOrchestrationPort} is missing, or when the requested
 * {@code embabelWorkflow} isn't registered with the platform. The
 * latter case produces a Levenshtein "did you mean?" suggestion.
 */
class PipelineValidatorEmbabelSpec extends Specification {

    PipelineRegistry registry = new PipelineRegistry()
    ApplicationContext ctx = Mock()
    ChannelRegistry channels = Mock()
    ObjectProvider<ChannelRegistry> channelsProvider = Mock()
    AgentOrchestrationPort port = Mock()
    ObjectProvider<AgentOrchestrationPort> portProvider = Mock()

    def setup() {
        channelsProvider.getIfAvailable() >> channels
    }

    private PipelineValidator validator() {
        return new PipelineValidator(
                registry, PipelineProperties.DEFAULT, ctx,
                channelsProvider, portProvider)
    }

    private static StageDefinition embabelStage(String name, String workflow) {
        return new StageDefinition(
                name, StageType.AGENT, null, null, null, null, null, null, null,
                StageRuntime.EMBABEL, workflow)
    }

    private static PipelineDefinition pipeline(String id, List<StageDefinition> stages) {
        return new PipelineDefinition(
                id, null, null, List.of(), true,
                null, ErrorStrategy.RETRY_THEN_FAIL, 3, null, stages, null, null)
    }

    def "EMBABEL stage with no AgentOrchestrationPort bean fails validation"() {
        given:
        portProvider.getIfAvailable() >> null
        registry.register(pipeline("p1", [embabelStage("classify", "invoice-classifier")]))

        when:
        ValidationReport report = validator().validate()

        then:
        report.hasErrors()
        ValidationError err = report.byPipeline().get("p1")
                .find { it.code() == "EMBABEL_RUNTIME_UNAVAILABLE" }
        err != null
        err.message().contains("jaiclaw-starter-embabel")
    }

    def "EMBABEL stage with unknown workflow fails validation with a suggestion"() {
        given:
        portProvider.getIfAvailable() >> port
        port.isAvailable() >> true
        port.platformName() >> "embabel"
        port.listWorkflows() >> [
                WorkflowDescriptor.of("invoice-classifier", "Classifies invoices"),
                WorkflowDescriptor.of("po-extractor", "Extracts PO data")
        ]
        registry.register(pipeline("p1", [embabelStage("classify", "invoice-classifer")]))   // typo

        when:
        ValidationReport report = validator().validate()

        then:
        report.hasErrors()
        ValidationError err = report.byPipeline().get("p1")
                .find { it.code() == "UNKNOWN_EMBABEL_WORKFLOW" }
        err != null
        err.suggestion() == "invoice-classifier"
    }

    def "EMBABEL stage with a registered workflow passes validation"() {
        given:
        portProvider.getIfAvailable() >> port
        port.isAvailable() >> true
        port.platformName() >> "embabel"
        port.listWorkflows() >> [WorkflowDescriptor.of("invoice-classifier", "")]
        registry.register(pipeline("p1", [embabelStage("classify", "invoice-classifier")]))

        when:
        ValidationReport report = validator().validate()

        then:
        !report.hasErrors()
    }

    def "NATIVE-runtime AGENT stages do NOT trigger EMBABEL checks even if port is missing"() {
        given:
        portProvider.getIfAvailable() >> null
        StageDefinition nativeStage = new StageDefinition(
                "classify", StageType.AGENT, null, "default", null, null, null, null, null)
        registry.register(pipeline("p1", [nativeStage]))

        when:
        ValidationReport report = validator().validate()

        then: "no EMBABEL_* errors raised for a NATIVE stage"
        !report.hasErrors()
    }
}
