package io.jaiclaw.pipeline

import org.apache.camel.Exchange
import org.apache.camel.Message
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import java.time.Instant
import java.util.function.Function

/**
 * Verifies {@link BeanStageProcessor} dispatches to either the legacy
 * {@code Function<String,String>} bean shape or the new
 * {@link ConfigurableStageProcessor} shape based on the bean's runtime
 * type, and that per-stage {@code config} is threaded through only for
 * the configurable variant.
 */
class ConfigurableStageProcessorDispatchSpec extends Specification {

    ApplicationContext ctx = Mock()
    Exchange exchange = Mock()
    Message message = Mock()
    PipelineContext pipelineContext = new PipelineContext(
            "p1", "e1", null, "c1", 0, 1, null, null,
            [:] as Map, [:] as Map)

    BeanStageProcessor processor = new BeanStageProcessor(ctx)

    def setup() {
        exchange.getIn() >> message
    }

    def "dispatches to ConfigurableStageProcessor with the stage's config"() {
        given:
        Map<String, String> config = [flavour: "chocolate", topping: "sprinkles"]
        StageDefinition stage = new StageDefinition(
                "s1", StageType.PROCESSOR, "cake",
                null, null, null, null, null, null,
                StageRuntime.NATIVE, null, config)
        ConfigurableStageProcessor bean = Mock()
        ctx.getBean("cake") >> bean

        when:
        processor.process(exchange, stage, pipelineContext)

        then:
        1 * bean.process(exchange, stage, pipelineContext, { Map m ->
            m.size() == 2 && m.flavour == "chocolate" && m.topping == "sprinkles"
        })
    }

    def "falls back to Function<String,String> for legacy beans"() {
        given:
        StageDefinition stage = new StageDefinition(
                "s1", StageType.PROCESSOR, "upper",
                null, null, null, null, null, null)
        Function<String, String> bean = { input -> input.toUpperCase() }
        ctx.getBean("upper") >> bean
        message.getBody(String.class) >> "hello"

        when:
        processor.process(exchange, stage, pipelineContext)

        then:
        1 * message.setBody("HELLO")
    }

    def "rejects a bean that is neither Configurable nor Function"() {
        given:
        StageDefinition stage = new StageDefinition(
                "s1", StageType.PROCESSOR, "weird",
                null, null, null, null, null, null)
        ctx.getBean("weird") >> new Object()

        when:
        processor.process(exchange, stage, pipelineContext)

        then:
        thrown(IllegalArgumentException)
    }

    def "empty config map is passed through — never null"() {
        given:
        StageDefinition stage = new StageDefinition(
                "s1", StageType.PROCESSOR, "cake",
                null, null, null, null, null, null,
                StageRuntime.NATIVE, null, null)
        ConfigurableStageProcessor bean = Mock()
        ctx.getBean("cake") >> bean

        when:
        processor.process(exchange, stage, pipelineContext)

        then:
        1 * bean.process(exchange, stage, pipelineContext, { Map m -> m.isEmpty() })
    }
}
