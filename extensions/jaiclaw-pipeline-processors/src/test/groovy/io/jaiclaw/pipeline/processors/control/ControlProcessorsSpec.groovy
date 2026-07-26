package io.jaiclaw.pipeline.processors.control

import io.jaiclaw.pipeline.PipelineContext
import io.jaiclaw.pipeline.PipelineRouteBuilder
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import org.apache.camel.Exchange
import org.apache.camel.Message
import spock.lang.Specification

class ControlProcessorsSpec extends Specification {

    Exchange exchange = Mock()
    Message message = Mock()
    PipelineContext ctx = new PipelineContext(
            "pipe", "exec", null, null, 0, 1, null, null,
            [:] as Map, [:] as Map)
    StageDefinition stage = new StageDefinition(
            "gate", StageType.PROCESSOR, "b", null, null, null, null, null, null)

    def setup() { exchange.getIn() >> message }

    def "FilterGate contains passes matching input"() {
        given:
        FilterGate p = new FilterGate()
        message.getBody(String.class) >> "hello urgent request"

        when:
        p.process(exchange, stage, ctx, [kind: "contains", predicate: "urgent"])

        then:
        noExceptionThrown()
        // stop-silently default; no property set on pass
    }

    def "FilterGate contains sets stop-property on failure"() {
        given:
        FilterGate p = new FilterGate()
        message.getBody(String.class) >> "hello mild request"

        when:
        p.process(exchange, stage, ctx,
                [kind: "contains", predicate: "urgent", onFail: "stop-silently"])

        then:
        1 * exchange.setProperty(FilterGate.STOPPED_PROPERTY, true)
    }

    def "FilterGate onFail=error throws"() {
        given:
        FilterGate p = new FilterGate()
        message.getBody(String.class) >> "hello mild"

        when:
        p.process(exchange, stage, ctx,
                [kind: "contains", predicate: "urgent", onFail: "error"])

        then:
        thrown(IllegalStateException)
    }

    def "FilterGate regex is applied against the body"() {
        given:
        FilterGate p = new FilterGate()
        message.getBody(String.class) >> "order-12345"

        when:
        p.process(exchange, stage, ctx, [kind: "regex", predicate: "order-\\d+"])

        then:
        noExceptionThrown()
    }

    def "FilterGate jsonpath passes when path resolves"() {
        given:
        FilterGate p = new FilterGate()
        message.getBody(String.class) >> '{"status":"active"}'

        when:
        p.process(exchange, stage, ctx, [kind: "jsonpath", predicate: '$.status'])

        then:
        noExceptionThrown()
    }

    def "SetMetadata writes to the stage-meta exchange property"() {
        given:
        SetMetadata p = new SetMetadata()

        when:
        p.process(exchange, stage, ctx, [key: "label", value: "important"])

        then:
        1 * exchange.setProperty(
                PipelineRouteBuilder.STAGE_METADATA_PREFIX + "label",
                "important")
    }

    def "SetMetadata renders template values"() {
        given:
        SetMetadata p = new SetMetadata()
        PipelineContext withInput = new PipelineContext(
                "p", "e", null, null, 0, 1, null, null,
                [:] as Map, ["__input__": "42"] as Map)

        when:
        p.process(exchange, stage, withInput,
                [key: "extracted", value: "id-{{input}}"])

        then:
        1 * exchange.setProperty(
                PipelineRouteBuilder.STAGE_METADATA_PREFIX + "extracted",
                "id-42")
    }

    def "SetMetadata rejects blank key"() {
        given:
        SetMetadata p = new SetMetadata()

        when:
        p.process(exchange, stage, ctx, [key: "", value: "v"])

        then:
        thrown(IllegalArgumentException)
    }
}
