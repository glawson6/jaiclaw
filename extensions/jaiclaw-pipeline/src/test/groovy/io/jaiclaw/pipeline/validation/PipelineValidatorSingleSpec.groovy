package io.jaiclaw.pipeline.validation

import io.jaiclaw.pipeline.ConfigurableStageProcessor
import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.OutputDefinition
import io.jaiclaw.pipeline.OutputType
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.PipelineSecurityProperties
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerDefinition
import io.jaiclaw.pipeline.TriggerType
import org.apache.camel.Exchange
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import java.util.function.Function

/**
 * The per-definition {@link PipelineValidator#validate(PipelineDefinition)}
 * overload — used by the Studio's validate-a-draft endpoint. Also
 * covers the widened PROCESSOR bean-type check that now accepts
 * {@link ConfigurableStageProcessor} alongside {@code Function<String,String>}.
 */
class PipelineValidatorSingleSpec extends Specification {

    PipelineRegistry registry = new PipelineRegistry()
    ApplicationContext ctx = Mock()
    ObjectProvider<?> channelRegistryProvider = Mock()

    PipelineValidator validator = new PipelineValidator(
            registry, null, ctx, channelRegistryProvider)

    private static PipelineDefinition definition(StageDefinition... stages) {
        return new PipelineDefinition(
                "p1", "p1", null, [] as List<String>, true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                stages as List,
                new OutputDefinition(OutputType.NONE, null, null, null),
                PipelineSecurityProperties.DEFAULT,
                null)
    }

    def "validate(null) returns an empty report"() {
        when:
        ValidationReport report = validator.validate(null)

        then:
        !report.hasErrors()
    }

    def "unknown-stage reference in systemPrompt is flagged"() {
        given:
        PipelineDefinition definition = definition(
                new StageDefinition("only", StageType.AGENT, null, "a1",
                        "hello {{stages.missing.output}}", null, null, null, null))

        when:
        ValidationReport report = validator.validate(definition)

        then:
        report.hasErrors()
        report.byPipeline()["p1"].any { it.code() == "UNKNOWN_STAGE_REF" }
    }

    def "PROCESSOR bean that implements Function is accepted"() {
        given:
        Function<String, String> bean = { it }
        ctx.containsBean("upper") >> true
        ctx.getType("upper") >> bean.getClass()
        PipelineDefinition definition = definition(
                new StageDefinition("s1", StageType.PROCESSOR, "upper",
                        null, null, null, null, null, null))

        when:
        ValidationReport report = validator.validate(definition)

        then:
        !report.hasErrors()
    }

    def "PROCESSOR bean that implements ConfigurableStageProcessor is accepted"() {
        given:
        Class<?> beanType = FakeConfigurable.class
        ctx.containsBean("myConfig") >> true
        ctx.getType("myConfig") >> beanType
        PipelineDefinition definition = definition(
                new StageDefinition("s1", StageType.PROCESSOR, "myConfig",
                        null, null, null, null, null, null))

        when:
        ValidationReport report = validator.validate(definition)

        then:
        !report.hasErrors()
    }

    def "PROCESSOR bean that implements neither is rejected"() {
        given:
        ctx.containsBean("weird") >> true
        ctx.getType("weird") >> String.class
        PipelineDefinition definition = definition(
                new StageDefinition("s1", StageType.PROCESSOR, "weird",
                        null, null, null, null, null, null))

        when:
        ValidationReport report = validator.validate(definition)

        then:
        report.hasErrors()
        report.byPipeline()["p1"].any { it.code() == "WRONG_BEAN_TYPE" }
    }

    // --- ID_BLANK — the fix for the /validate 500 issue ---

    def "PipelineDefinition can now be constructed with a blank id (previously threw)"() {
        // This is the positive assertion of the guard removal in
        // PipelineDefinition's compact constructor. The record is a legal
        // value; boundary layers (registry, validator) enforce non-blank.
        when:
        PipelineDefinition definition = new PipelineDefinition(
                "", "", null, [] as List<String>, true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [] as List,
                new OutputDefinition(OutputType.NONE, null, null, null),
                PipelineSecurityProperties.DEFAULT,
                null)

        then:
        noExceptionThrown()
        definition.id() == ""
    }

    def "blank id produces a single ID_BLANK ValidationError under the '*' pipeline key"() {
        given:
        PipelineDefinition definition = new PipelineDefinition(
                "", "", null, [] as List<String>, true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [] as List,
                new OutputDefinition(OutputType.NONE, null, null, null),
                PipelineSecurityProperties.DEFAULT,
                null)

        when:
        ValidationReport report = validator.validate(definition)

        then:
        report.hasErrors()
        // ValidationError's compact constructor coerces a blank pipelineId
        // to "*" — that's the key the report uses.
        List<ValidationError> errors = report.byPipeline()["*"]
        errors != null
        errors.size() == 1
        errors[0].code() == "ID_BLANK"
        errors[0].location() == "pipeline"
    }

    def "blank id short-circuits — downstream stage checks don't cascade with garbage errors"() {
        given: "a definition with blank id AND a busted stage placeholder (would normally produce UNKNOWN_STAGE_REF)"
        PipelineDefinition definition = new PipelineDefinition(
                "", "", null, [] as List<String>, true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [new StageDefinition("only", StageType.AGENT, null, "a1",
                        "hello {{stages.missing.output}}", null, null, null, null)] as List,
                new OutputDefinition(OutputType.NONE, null, null, null),
                PipelineSecurityProperties.DEFAULT,
                null)

        when:
        ValidationReport report = validator.validate(definition)

        then: "exactly one error — no UNKNOWN_STAGE_REF cascade"
        report.totalErrors() == 1
        report.byPipeline()["*"][0].code() == "ID_BLANK"
    }

    // Fixture — a class that implements the new SPI so getType() returns
    // an assignable class in the "Configurable is accepted" case above.
    static class FakeConfigurable implements ConfigurableStageProcessor {
        @Override
        void process(Exchange exchange, StageDefinition stage,
                     io.jaiclaw.pipeline.PipelineContext context,
                     Map<String, String> config) {}
    }
}
