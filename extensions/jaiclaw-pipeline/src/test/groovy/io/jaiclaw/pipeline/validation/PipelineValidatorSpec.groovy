package io.jaiclaw.pipeline.validation

import io.jaiclaw.channel.ChannelRegistry
import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.OutputDefinition
import io.jaiclaw.pipeline.OutputType
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineProperties
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import java.util.function.Function

class PipelineValidatorSpec extends Specification {

    PipelineRegistry registry = new PipelineRegistry()
    ApplicationContext ctx = Mock()
    ChannelRegistry channels = Mock()
    ObjectProvider<ChannelRegistry> channelsProvider = Mock()

    def setup() {
        channelsProvider.getIfAvailable() >> channels
    }

    private PipelineValidator validator(PipelineProperties props = PipelineProperties.DEFAULT) {
        return new PipelineValidator(registry, props, ctx, channelsProvider)
    }

    private static StageDefinition processorStage(String name, String bean, String systemPrompt = null) {
        return new StageDefinition(name, StageType.PROCESSOR, bean, null, systemPrompt, null, null, null, null)
    }

    private static PipelineDefinition pipeline(String id,
                                               List<StageDefinition> stages,
                                               OutputDefinition output = null,
                                               ErrorStrategy err = null,
                                               String dlq = null) {
        return new PipelineDefinition(
                id, null, null, List.of(), true,
                null, err, 3, dlq, stages, output, null)
    }

    def "valid pipeline produces no errors"() {
        given:
        ctx.containsBean("beanA") >> true
        ctx.containsBean("beanB") >> true
        ctx.getType("beanA") >> Function
        ctx.getType("beanB") >> Function
        registry.register(pipeline("p1", [
                processorStage("research", "beanA"),
                processorStage("write", "beanB", "use {{stages.research.output}}")
        ]))

        when:
        ValidationReport report = validator().validate()

        then:
        !report.hasErrors()
    }

    def "unknown stage reference flagged with suggestion"() {
        given:
        ctx.containsBean(_ as String) >> true
        ctx.getType(_ as String) >> Function
        registry.register(pipeline("content-pipeline", [
                processorStage("research", "beanA"),
                processorStage("write", "beanB", "use {{stages.resarch.output}}")
        ]))

        when:
        ValidationReport report = validator().validate()

        then:
        report.hasErrors()
        ValidationError err = report.byPipeline().get("content-pipeline").find { it.code() == "UNKNOWN_STAGE_REF" }
        err != null
        err.suggestion() == "research"
        err.formatted().contains("did you mean 'research'?")
    }

    def "processor stage with missing bean flagged"() {
        given:
        ctx.containsBean("missing") >> false
        ctx.getBeanNamesForType(Function) >> (new String[0])
        registry.register(pipeline("p", [processorStage("s1", "missing")]))

        when:
        ValidationReport report = validator().validate()

        then:
        report.byPipeline().get("p").any { it.code() == "UNKNOWN_BEAN" }
    }

    def "processor stage with wrong bean type flagged"() {
        given:
        ctx.containsBean("notAFunction") >> true
        ctx.getType("notAFunction") >> String
        registry.register(pipeline("p", [processorStage("s1", "notAFunction")]))

        when:
        ValidationReport report = validator().validate()

        then:
        ValidationError err = report.byPipeline().get("p").find { it.code() == "WRONG_BEAN_TYPE" }
        err != null
        err.message().contains("java.lang.String")
    }

    def "unknown channelId in output flagged with suggestion"() {
        given:
        ctx.containsBean(_ as String) >> true
        ctx.getType(_ as String) >> Function
        channels.contains("slakk") >> false
        channels.channelIds() >> (["slack", "teams"] as Set)
        OutputDefinition output = new OutputDefinition(OutputType.CHANNEL, "slakk", null, null)
        registry.register(pipeline("p", [processorStage("s1", "beanA")], output))

        when:
        ValidationReport report = validator().validate()

        then:
        ValidationError err = report.byPipeline().get("p").find { it.code() == "UNKNOWN_CHANNEL" }
        err != null
        err.suggestion() == "slack"
        err.message().contains("[slack, teams]")
    }

    def "known channelId is accepted"() {
        given:
        ctx.containsBean(_ as String) >> true
        ctx.getType(_ as String) >> Function
        channels.contains("slack") >> true
        OutputDefinition output = new OutputDefinition(OutputType.CHANNEL, "slack", null, null)
        registry.register(pipeline("p", [processorStage("s1", "beanA")], output))

        when:
        ValidationReport report = validator().validate()

        then:
        !report.hasErrors()
    }

    def "dead-letter strategy without uri or default fails"() {
        given:
        ctx.containsBean(_ as String) >> true
        ctx.getType(_ as String) >> Function
        registry.register(pipeline("p", [processorStage("s1", "beanA")], null, ErrorStrategy.DEAD_LETTER, null))

        when:
        ValidationReport report = validator().validate()

        then:
        report.byPipeline().get("p").any { it.code() == "MISSING_DEAD_LETTER_URI" }
    }

    def "dead-letter strategy with global default passes"() {
        given:
        ctx.containsBean(_ as String) >> true
        ctx.getType(_ as String) >> Function
        PipelineProperties.PipelineDefaults defaults = new PipelineProperties.PipelineDefaults(100, 5, true, "log:dlq")
        PipelineProperties props = new PipelineProperties(true, null, defaults, null, null, null, null, null)
        registry.register(pipeline("p", [processorStage("s1", "beanA")], null, ErrorStrategy.DEAD_LETTER, null))

        when:
        ValidationReport report = validator(props).validate()

        then:
        !report.hasErrors()
    }

    def "channel validation skipped when ChannelRegistry absent"() {
        given:
        ObjectProvider<ChannelRegistry> emptyProvider = Mock()
        emptyProvider.getIfAvailable() >> null
        PipelineValidator v = new PipelineValidator(registry, PipelineProperties.DEFAULT, ctx, emptyProvider)
        ctx.containsBean(_ as String) >> true
        ctx.getType(_ as String) >> Function
        OutputDefinition output = new OutputDefinition(OutputType.CHANNEL, "anything", null, null)
        registry.register(pipeline("p", [processorStage("s1", "beanA")], output))

        when:
        ValidationReport report = v.validate()

        then:
        !report.hasErrors()
    }

    def "validateOrThrow throws on errors with consolidated message"() {
        given:
        ctx.containsBean(_ as String) >> true
        ctx.getType(_ as String) >> Function
        channels.contains("slakk") >> false
        channels.channelIds() >> (["slack", "teams"] as Set)
        OutputDefinition output = new OutputDefinition(OutputType.CHANNEL, "slakk", null, null)
        registry.register(pipeline("content-pipeline", [
                processorStage("research", "beanA"),
                processorStage("write", "beanB", "use {{stages.resarch.output}}")
        ], output))

        when:
        validator().validateOrThrow()

        then:
        IllegalStateException ex = thrown()
        ex.message.contains("content-pipeline")
        ex.message.contains("UNKNOWN_STAGE_REF") || ex.message.contains("unknown stage 'resarch'")
        ex.message.contains("UNKNOWN_CHANNEL") || ex.message.contains("'slakk'")
    }

    def "validateOrThrow does not throw on clean registry"() {
        given:
        ctx.containsBean(_ as String) >> true
        ctx.getType(_ as String) >> Function
        registry.register(pipeline("p", [processorStage("s1", "beanA")]))

        when:
        validator().validateOrThrow()

        then:
        notThrown(Exception)
    }
}
